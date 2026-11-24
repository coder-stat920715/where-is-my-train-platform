package com.wimt.ingestion.api;

import com.wimt.ingestion.sim.TelemetrySimulator;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Toggle for the built-in live telemetry simulator (train 22895, HWH -> PURI). */
@RestController
@RequestMapping("/api/v1/simulator")
public class SimulatorController {

    private final TelemetrySimulator simulator;

    public SimulatorController(TelemetrySimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/start")
    public Map<String, Object> start() {
        return Map.of("running", true, "message", simulator.start());
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        simulator.stop();
        return Map.of("running", false);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return simulator.status();
    }

    /** Emits an impossible station jump (HWH -> PURI) to demonstrate saga compensation. */
    @PostMapping("/inject-anomaly")
    public TelemetryResponse injectAnomaly() {
        return simulator.injectAnomaly();
    }
}
