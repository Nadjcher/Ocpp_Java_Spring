// frontend/src/components/simu-evse/TNRBandeau.tsx
// Composant TNR Bandeau extrait de SimuEvseTab.tsx

import React from 'react';
import type { TNRHook } from '@/hooks/useEvseHooks';

interface TNRBandeauProps {
  tnr: TNRHook;
  onOpenTNR: () => void;
}

/**
 * Bandeau d'enregistrement TNR (Test & Replay)
 * Permet de démarrer/arrêter l'enregistrement de scénarios OCPP
 */
export const TNRBandeau: React.FC<TNRBandeauProps> = ({ tnr, onOpenTNR }) => (
  <div className="rounded border bg-white p-3 shadow-sm">
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-3">
        <div className="font-semibold">TNR Recording</div>
        <div className="text-xs text-slate-600">
          (Test & Replay - Enregistrement de scénarios OCPP)
        </div>
        {tnr.isRecording && (
          <div className="ml-4 flex items-center gap-2">
            <span className="relative flex h-3 w-3">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-500 opacity-75" />
              <span className="relative inline-flex rounded-full h-3 w-3 bg-rose-600" />
            </span>
            <span className="text-sm font-medium text-rose-600">
              REC - {tnr.recEvents} events
            </span>
          </div>
        )}
      </div>
      <button
        className="px-3 py-2 bg-slate-100 rounded hover:bg-slate-200 text-sm"
        onClick={onOpenTNR}
      >
        Ouvrir TNR
      </button>
    </div>

    <div className="mt-3 grid grid-cols-12 gap-2 items-end">
      <div className="col-span-3">
        <div className="text-xs mb-1 text-slate-600">Nom du scénario</div>
        <input
          className="w-full border rounded px-2 py-1 text-sm"
          value={tnr.recName}
          onChange={(e) => tnr.setRecName(e.target.value)}
          disabled={tnr.isRecording}
          placeholder="Ex: Test_Charge_Complete"
        />
      </div>

      <div className="col-span-3">
        <div className="text-xs mb-1 text-slate-600">Tags (séparés par virgule)</div>
        <input
          className="w-full border rounded px-2 py-1 text-sm"
          value={tnr.recTags}
          onChange={(e) => tnr.setRecTags(e.target.value)}
          disabled={tnr.isRecording}
          placeholder="test, charge, ac"
        />
      </div>

      <label className="col-span-1 text-sm inline-flex items-center gap-2">
        <input
          type="checkbox"
          checked={tnr.recBaseline}
          onChange={(e) => tnr.setRecBaseline(e.target.checked)}
          disabled={tnr.isRecording}
        />
        <span className="text-xs">Baseline</span>
      </label>

      <div className="col-span-1 text-right">
        {!tnr.isRecording ? (
          <button
            className="px-3 py-2 bg-rose-600 text-white rounded hover:bg-rose-700 transition-colors w-full font-medium text-sm"
            onClick={async () => {
              const success = await tnr.startRecording();
              if (!success) {
                console.error("TNR: Échec du démarrage de l'enregistrement");
              }
            }}
          >
            Start
          </button>
        ) : (
          <button
            className="px-3 py-2 bg-emerald-600 text-white rounded hover:bg-emerald-700 transition-colors w-full font-medium text-sm animate-pulse"
            onClick={async () => {
              const success = await tnr.stopRecording();
              if (!success) {
                console.error("TNR: Échec de l'arrêt de l'enregistrement");
              }
            }}
          >
            Stop & Save
          </button>
        )}
      </div>
    </div>

    {/* Champ description en dessous */}
    <div className="mt-2">
      <div className="text-xs mb-1 text-slate-600">Description du scénario (optionnel)</div>
      <textarea
        className="w-full border rounded px-2 py-1 text-sm"
        value={tnr.recDescription}
        onChange={(e) => tnr.setRecDescription(e.target.value)}
        disabled={tnr.isRecording}
        placeholder="Décrivez le scénario de test..."
        rows={2}
        style={{ resize: 'vertical', minHeight: '40px' }}
      />
    </div>

    {tnr.isRecording && (
      <div className="mt-2 p-2 bg-rose-50 rounded-lg border border-rose-200">
        <div className="text-xs text-rose-700">
          Enregistrement en cours... Toutes les actions OCPP sont capturées.
        </div>
      </div>
    )}
  </div>
);

export default TNRBandeau;
