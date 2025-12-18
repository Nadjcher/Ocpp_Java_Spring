package com.evse.simulator.controller;

import com.evse.simulator.domain.service.TNRService;
import com.evse.simulator.model.ExecutionMeta;
import com.evse.simulator.model.TnrPlusCompareResult;
import com.evse.simulator.service.TnrPlusDiffService;
import com.evse.simulator.service.TnrPlusDiffService.DiffOptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Contrôleur pour les fonctionnalités TNR+ (comparaison avancée d'exécutions).
 */
@RestController
@RequestMapping("/api/tnrplus")
@Tag(name = "TNR+", description = "Fonctionnalités avancées TNR : comparaison d'exécutions, export CSV")
public class TnrPlusController {

    private final TNRService tnr;
    private final TnrPlusDiffService diffs;

    public TnrPlusController(TNRService tnr, TnrPlusDiffService diffs) {
        this.tnr = tnr;
        this.diffs = diffs;
    }

    /**
     * Liste des exécutions (métadonnées) depuis TNRService.
     */
    @Operation(summary = "Liste les exécutions TNR disponibles")
    @GetMapping("/executions")
    public List<ExecutionMeta> executions() throws Exception {
        return tnr.listExecutions();
    }

    /**
     * Requête de comparaison entre deux exécutions.
     */
    public record CompareReq(
            String baseline,
            String current,
            List<String> ignoreKeys,
            Boolean strictOrder,
            Boolean allowExtras,
            Double numberTolerance
    ) {}

    /**
     * Compare deux exécutions.
     */
    @Operation(summary = "Compare deux exécutions TNR")
    @PostMapping("/compare")
    public TnrPlusCompareResult compare(@RequestBody CompareReq req) throws Exception {
        DiffOptions opt = new DiffOptions();
        if (req.ignoreKeys() != null && !req.ignoreKeys().isEmpty()) {
            opt.setIgnoreKeys(req.ignoreKeys());
        }
        if (req.strictOrder() != null) {
            opt.setStrictOrder(req.strictOrder());
        }
        if (req.allowExtras() != null) {
            opt.setAllowExtras(req.allowExtras());
        }
        if (req.numberTolerance() != null) {
            opt.setNumberTolerance(req.numberTolerance());
        }
        return diffs.compare(req.baseline(), req.current(), opt);
    }

    /**
     * Export CSV des différences (utile pour bug report).
     */
    @Operation(summary = "Exporte les différences au format CSV")
    @PostMapping(value = "/compare/export", produces = "text/csv")
    public ResponseEntity<byte[]> compareExport(@RequestBody CompareReq req) throws Exception {
        TnrPlusCompareResult r = compare(req);
        StringBuilder sb = new StringBuilder("eventIndex,path,type,expected,actual\n");
        for (var d : r.getDifferences()) {
            sb.append(escape(d.getEventIndex()))
                    .append(',').append(csv(d.getPath()))
                    .append(',').append(csv(d.getType()))
                    .append(',').append(csv(String.valueOf(d.getExpected())))
                    .append(',').append(csv(String.valueOf(d.getActual())))
                    .append('\n');
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tnr-diff.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    private String csv(String s) {
        if (s == null) return "";
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    private String escape(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}