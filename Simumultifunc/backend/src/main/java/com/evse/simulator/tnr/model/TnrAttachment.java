package com.evse.simulator.tnr.model;

import lombok.*;

/**
 * Pièce jointe à un événement TNR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TnrAttachment {
    private String name;
    private String type;
    private String content;
    private String encoding;
    private long size;
}
