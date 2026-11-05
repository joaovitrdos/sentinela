package br.com.sentinela.controller;

import br.com.sentinela.enums.Feature;
import br.com.sentinela.infra.security.RequiresFeature;
import br.com.sentinela.dto.response.CarPropertyResponseDto;
import br.com.sentinela.service.CarService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiresFeature(Feature.BASE)
@RestController @RequiredArgsConstructor
@RequestMapping("/api/car")
public class CarController {
    private final CarService service;

    @GetMapping("/{code}")
    public CarPropertyResponseDto find(@PathVariable String code) {
        return service.find(code);
    }
}
