package com.wimt.saga.api;

import com.wimt.common.SagaStatus;
import com.wimt.saga.domain.SagaInstance;
import com.wimt.saga.domain.SagaInstanceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only saga inspection API (operations / debugging). */
@RestController
@RequestMapping("/api/v1/sagas")
public class SagaController {
    private final SagaInstanceRepository sagas;

    public SagaController(SagaInstanceRepository sagas) {
        this.sagas = sagas;
    }

    @GetMapping
    public List<SagaInstance> latest(@RequestParam(required = false) SagaStatus state) {
        return state == null ? sagas.findTop50ByOrderByCreatedAtDesc() : sagas.findTop50ByStateOrderByCreatedAtDesc(state);
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (SagaStatus s : SagaStatus.values()) {
            m.put(s.name(), 0L);
        }
        for (Object[] row : sagas.countByState()) {
            m.put(String.valueOf(row[0]), (Long) row[1]);
        }
        return m;
    }

    @GetMapping("/{id}")
    public ResponseEntity<SagaInstance> get(@PathVariable UUID id) {
        return sagas.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
