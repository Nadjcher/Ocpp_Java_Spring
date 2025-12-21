/**
 * GPMResultsPanel - Résultats et graphiques de simulation
 */

import React from 'react';
import { useGPMStore } from '@/store/gpmStore';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  AreaChart,
  Area,
} from 'recharts';

export const GPMResultsPanel: React.FC = () => {
  const { simulation } = useGPMStore();

  if (!simulation || !simulation.tickResults || simulation.tickResults.length === 0) {
    return (
      <div className="bg-white rounded-lg border border-gray-200 p-4">
        <h3 className="text-lg font-semibold text-gray-800 mb-4">
          Résultats
        </h3>
        <div className="text-center py-8 text-gray-500">
          Aucun résultat disponible
        </div>
      </div>
    );
  }

  // Préparer les données pour les graphiques
  const chartData = simulation.tickResults.map((tick) => ({
    tick: tick.tick,
    powerKW: tick.totalPowerW / 1000,
    energyKWh: tick.totalEnergyWh / 1000,
    time: new Date(tick.simulatedTime).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }),
  }));

  // Données SOC par véhicule
  const vehicleSocData = simulation.tickResults.map((tick) => {
    const data: Record<string, number | string> = { tick: tick.tick };
    tick.vehicleResults.forEach((v) => {
      data[v.evseId] = v.socAfter;
    });
    return data;
  });

  const vehicleIds = simulation.vehicles.map((v) => v.evseId);
  const colors = ['#3b82f6', '#22c55e', '#f97316', '#8b5cf6', '#ef4444', '#06b6d4'];

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-6">
      <h3 className="text-lg font-semibold text-gray-800">
        Résultats
      </h3>

      {/* Stats globales */}
      <div className="grid grid-cols-4 gap-4">
        <StatCard
          label="Énergie Totale"
          value={`${(simulation.totalEnergyWh / 1000).toFixed(2)} kWh`}
          icon="⚡"
        />
        <StatCard
          label="Puissance Moyenne"
          value={`${(simulation.averagePowerW / 1000).toFixed(1)} kW`}
          icon="📊"
        />
        <StatCard
          label="Puissance Pic"
          value={`${(simulation.peakPowerW / 1000).toFixed(1)} kW`}
          icon="📈"
        />
        <StatCard
          label="Erreurs API"
          value={`${simulation.errorCount}`}
          icon={simulation.errorCount > 0 ? '⚠️' : '✓'}
          variant={simulation.errorCount > 0 ? 'warning' : 'success'}
        />
      </div>

      {/* Graphique Puissance */}
      <div>
        <h4 className="text-md font-medium text-gray-700 mb-2">Puissance Totale (kW)</h4>
        <div className="h-64">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="tick" label={{ value: 'Tick', position: 'bottom' }} />
              <YAxis label={{ value: 'kW', angle: -90, position: 'insideLeft' }} />
              <Tooltip
                formatter={(value: number) => [`${value.toFixed(1)} kW`, 'Puissance']}
                labelFormatter={(tick) => `Tick ${tick}`}
              />
              <Area
                type="monotone"
                dataKey="powerKW"
                stroke="#3b82f6"
                fill="#93c5fd"
                strokeWidth={2}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Graphique SOC par véhicule */}
      {vehicleIds.length > 0 && (
        <div>
          <h4 className="text-md font-medium text-gray-700 mb-2">SOC par Véhicule (%)</h4>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={vehicleSocData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="tick" />
                <YAxis domain={[0, 100]} />
                <Tooltip formatter={(value: number) => [`${value.toFixed(1)}%`, 'SOC']} />
                <Legend />
                {vehicleIds.map((id, i) => (
                  <Line
                    key={id}
                    type="monotone"
                    dataKey={id}
                    stroke={colors[i % colors.length]}
                    strokeWidth={2}
                    dot={false}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Erreurs API */}
      {simulation.apiErrors.length > 0 && (
        <div>
          <h4 className="text-md font-medium text-gray-700 mb-2">Erreurs API</h4>
          <div className="max-h-40 overflow-y-auto space-y-2">
            {simulation.apiErrors.map((error, i) => (
              <div key={i} className="p-2 bg-red-50 border border-red-200 rounded text-sm">
                <div className="flex justify-between text-red-700">
                  <span>Tick {error.tick} - {error.type}</span>
                  <span>{error.evseId}</span>
                </div>
                <div className="text-red-600 text-xs">{error.message}</div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

interface StatCardProps {
  label: string;
  value: string;
  icon: string;
  variant?: 'default' | 'success' | 'warning';
}

const StatCard: React.FC<StatCardProps> = ({ label, value, icon, variant = 'default' }) => {
  const bgColor = variant === 'success' ? 'bg-green-50' : variant === 'warning' ? 'bg-yellow-50' : 'bg-gray-50';
  const textColor = variant === 'success' ? 'text-green-700' : variant === 'warning' ? 'text-yellow-700' : 'text-gray-700';

  return (
    <div className={`p-3 rounded-lg ${bgColor}`}>
      <div className="flex items-center gap-2 mb-1">
        <span>{icon}</span>
        <span className="text-xs text-gray-500">{label}</span>
      </div>
      <div className={`text-lg font-semibold ${textColor}`}>{value}</div>
    </div>
  );
};

export default GPMResultsPanel;
