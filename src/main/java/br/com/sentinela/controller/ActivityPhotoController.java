package br.com.sentinela.controller;

import br.com.sentinela.util.Util;

import br.com.sentinela.entity.ActivityPhoto;
import br.com.sentinela.entity.PropertyActivity;
import br.com.sentinela.entity.User;
import br.com.sentinela.repository.ActivityPhotoRepository;
import br.com.sentinela.repository.PropertyActivityRepository;
import br.com.sentinela.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/properties/{propertyId}/activities/{activityId}/photos")
public class ActivityPhotoController {
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final PropertyService properties;
    private final PropertyActivityRepository activities;
    private final ActivityPhotoRepository photos;

    private PropertyActivity activity(UUID propertyId, UUID activityId) {
        return activities.findById(activityId).filter(row -> row.getProperty().getPropertyId().equals(propertyId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado"));
    }

    @GetMapping
    public List<PhotoInfo> list(@AuthenticationPrincipal User user, @PathVariable UUID propertyId, @PathVariable UUID activityId) {
        properties.requireView(propertyId, user.getUserId());
        activity(propertyId, activityId);
        return photos.findAllByActivity_ActivityIdOrderByCreatedAtAsc(activityId).stream()
                .map(photo -> new PhotoInfo(photo.getPhotoId(), photo.getFileName())).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public PhotoInfo upload(@AuthenticationPrincipal User user, @PathVariable UUID propertyId, @PathVariable UUID activityId,
                            @RequestPart("file") MultipartFile file) throws IOException {
        properties.requireEdit(propertyId, user.getUserId());
        PropertyActivity activity = activity(propertyId, activityId);
        if (Util.isNull(file) || file.isEmpty() || file.getSize() > 5_000_000 || !TYPES.contains(file.getContentType()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie JPEG, PNG ou WebP com até 5 MB");
        if (photos.countByActivity_ActivityId(activityId) >= 5)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Limite de cinco fotos por registro");
        byte[] bytes = file.getBytes();
        if (!matchesImageSignature(file.getContentType(), bytes))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O conteúdo do arquivo não corresponde ao formato de imagem");
        String name = Util.isNull(file.getOriginalFilename()) ? "foto" : file.getOriginalFilename().replaceAll("[\\r\\n\\/\\\\]", "_");
        ActivityPhoto photo = photos.save(ActivityPhoto.builder().activity(activity).fileName(name)
                .contentType(file.getContentType()).imageData(bytes).build());
        return new PhotoInfo(photo.getPhotoId(), photo.getFileName());
    }

    @GetMapping("/{photoId}")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
            @PathVariable UUID activityId, @PathVariable UUID photoId) {
        properties.requireView(propertyId, user.getUserId());
        activity(propertyId, activityId);
        ActivityPhoto photo = photos.findByPhotoIdAndActivity_ActivityId(photoId, activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto não encontrada"));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300").body(photo.getImageData());
    }

    public record PhotoInfo(UUID photoId, String fileName) {}

    private boolean matchesImageSignature(String type, byte[] data) {
        if ("image/jpeg".equals(type)) return data.length > 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff;
        if ("image/png".equals(type)) return data.length > 8 && (data[0] & 0xff) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
        if ("image/webp".equals(type)) return data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
        return false;
    }
}
