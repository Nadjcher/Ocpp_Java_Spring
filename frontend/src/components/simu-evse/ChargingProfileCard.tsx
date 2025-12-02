// frontend/src/components/simu-evse/ChargingProfileCard.tsx
// Carte affichant l'état des profils de charge OCPP

import React, { useState, useEffect } from 'react';
import type { OCPPChargingProfilesManager, ChargingProfile } from '@/services/OCPPChargingProfilesManager';

interface ChargingProfileCardProps {
  profilesManager: OCPPChargingProfilesManager;
  connectorId: number;
}

interface ProfileState {
  limitW: number;
  source: string;
  profiles: ChargingProfile[];
}

export function ChargingProfileCard({
  profilesManager,
  connectorId,
}: ChargingProfileCardProps) {
  const [state, setState] = useState<ProfileState>({
    limitW: 0,
    source: 'default',
    profiles: [],
  });

  useEffect(() => {
    const update = () => {
      const connectorState = profilesManager.getConnectorState(connectorId);
      setState({
        limitW: connectorState.effectiveLimit.limitW,
        source:
          connectorState.effectiveLimit.source === 'profile'
            ? connectorState.effectiveLimit.purpose
            : 'Limite Physique',
        profiles: connectorState.profiles,
      });
    };

    update();
    const interval = setInterval(update, 500);
    return () => clearInterval(interval);
  }, [profilesManager, connectorId]);

  const getSourceBadgeClass = (source: string): string => {
    switch (source) {
      case 'Limite Physique':
        return 'bg-amber-100 text-amber-800';
      case 'TxProfile':
        return 'bg-green-100 text-green-800';
      case 'TxDefaultProfile':
        return 'bg-blue-100 text-blue-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="rounded-lg border border-emerald-500 bg-gradient-to-br from-emerald-50 to-white p-4 shadow-lg">
      <div className="text-sm font-bold text-emerald-700 mb-3">
        Charging Profiles OCPP 1.6
      </div>

      <div className="space-y-3">
        <div>
          <div className="text-xs text-gray-600 mb-1">Limite active:</div>
          <div className="flex items-center gap-3">
            <span className="text-2xl font-bold text-gray-900">
              {(state.limitW / 1000).toFixed(1)} kW
            </span>
          </div>
        </div>

        <div>
          <div className="text-xs text-gray-600 mb-1">Source:</div>
          <span
            className={`inline-block px-3 py-1 rounded-full text-sm font-medium ${getSourceBadgeClass(
              state.source
            )}`}
          >
            {state.source}
          </span>
        </div>

        {state.profiles.length > 0 && (
          <div className="pt-2 border-t border-emerald-200">
            <div className="text-xs text-gray-600 mb-1">Profils actifs:</div>
            {state.profiles.map((p) => (
              <div key={p.chargingProfileId} className="text-xs text-gray-700">
                • #{p.chargingProfileId} - {p.chargingProfilePurpose} (Stack:{' '}
                {p.stackLevel})
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default ChargingProfileCard;
