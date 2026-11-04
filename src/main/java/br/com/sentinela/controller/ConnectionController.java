package br.com.sentinela.controller;

import br.com.sentinela.util.LogUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/connection")
public class ConnectionController {

    public enum ConnectionState { GOOD, UNSTABLE, POOR }

    public record ConnectionStatusRequest(
            @NotNull ConnectionState state,
            ConnectionState previousState,
            @NotNull @Min(-1) @Max(60000) Long latencyMs) {
    }

    @GetMapping("/probe")
    public ResponseEntity<Void> probe() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/status")
    public ResponseEntity<Void> receiveConnectionStatus(@Valid @RequestBody ConnectionStatusRequest status) {
        String message = "Client connection: {0}; previous: {1}; latency: {2} ms";
        Object[] arguments = {status.state(), status.previousState(), status.latencyMs()};
        switch (status.state()) {
            case GOOD -> LogUtil.info(message, arguments);
            case UNSTABLE -> LogUtil.warn(message, arguments);
            case POOR -> LogUtil.error(message, arguments);
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
