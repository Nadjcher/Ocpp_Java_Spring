package com.evse.simulator.model;

import lombok.Data;

import java.util.List;

/**
 * Requête pour créer ou modifier un profil de charge.
 */
@Data
public class ChargingProfileRequest {
    /**
     * ID de la session cible.
     */
    private String sessionId;

    /**
     * ID du connecteur.
     */
    private Integer connectorId;

    /**
     * ID du profil.
     */
    private Integer profileId;

    /**
     * Niveau de pile (stack level).
     */
    private Integer stackLevel;

    /**
     * But du profil (ChargePointMaxProfile, TxDefaultProfile, TxProfile).
     */
    private String purpose;

    /**
     * Type de profil (Absolute, Relative, Recurring).
     */
    private String kind;

    /**
     * Type de récurrence (Daily, Weekly).
     */
    private String recurrency;

    /**
     * Unité (A ou W).
     */
    private String unit;

    /**
     * Périodes de charge.
     */
    private List<ChargingProfile.ChargingSchedulePeriod> periods;
}