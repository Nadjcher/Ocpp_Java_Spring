/**
 * PartnerCard - Carte visuelle d'un partenaire OCPI
 */

import React from 'react';
import {
  OCPIPartner,
  OCPIEnvironment,
  getPartnerStatusColor,
  OCPI_ROLES,
} from './types';

interface PartnerCardProps {
  partner: OCPIPartner;
  isSelected?: boolean;
  onSelect?: () => void;
  onEdit?: () => void;
  onDelete?: () => void;
  onTestConnection?: () => void;
  onDiscover?: () => void;
  onSwitchEnvironment?: (envId: string) => void;
  testingConnection?: boolean;
  discovering?: boolean;
}

const styles: Record<string, React.CSSProperties> = {
  card: {
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: 8,
    padding: 16,
    marginBottom: 12,
    transition: 'all 0.2s ease',
    cursor: 'pointer',
  },
  cardSelected: {
    borderColor: '#3b82f6',
    boxShadow: '0 0 0 2px rgba(59, 130, 246, 0.2)',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  titleRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
  },
  statusDot: {
    width: 12,
    height: 12,
    borderRadius: '50%',
  },
  name: {
    fontSize: 16,
    fontWeight: 600,
    color: '#111827',
    margin: 0,
  },
  roleBadge: {
    fontSize: 11,
    fontWeight: 600,
    padding: '2px 8px',
    borderRadius: 4,
    color: '#fff',
  },
  info: {
    display: 'flex',
    alignItems: 'center',
    gap: 16,
    fontSize: 13,
    color: '#6b7280',
    marginBottom: 12,
  },
  infoItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 4,
  },
  envSection: {
    borderTop: '1px solid #f3f4f6',
    paddingTop: 12,
    marginBottom: 12,
  },
  envLabel: {
    fontSize: 11,
    fontWeight: 500,
    color: '#9ca3af',
    marginBottom: 6,
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
  },
  envList: {
    display: 'flex',
    flexWrap: 'wrap' as const,
    gap: 8,
  },
  envBadge: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
    padding: '4px 10px',
    borderRadius: 4,
    fontSize: 12,
    cursor: 'pointer',
    transition: 'all 0.15s ease',
    border: '1px solid transparent',
  },
  envBadgeActive: {
    background: '#dbeafe',
    color: '#1d4ed8',
    fontWeight: 500,
  },
  envBadgeInactive: {
    background: '#f3f4f6',
    color: '#6b7280',
  },
  envStatus: {
    width: 6,
    height: 6,
    borderRadius: '50%',
  },
  modulesSection: {
    marginBottom: 12,
  },
  modulesList: {
    display: 'flex',
    flexWrap: 'wrap' as const,
    gap: 6,
  },
  moduleBadge: {
    fontSize: 10,
    padding: '2px 6px',
    borderRadius: 3,
    background: '#f3f4f6',
    color: '#4b5563',
    textTransform: 'uppercase' as const,
  },
  actions: {
    display: 'flex',
    justifyContent: 'flex-end',
    gap: 8,
    borderTop: '1px solid #f3f4f6',
    paddingTop: 12,
  },
  btn: {
    padding: '6px 12px',
    borderRadius: 4,
    fontSize: 12,
    fontWeight: 500,
    cursor: 'pointer',
    border: 'none',
    transition: 'all 0.15s ease',
  },
  btnPrimary: {
    background: '#3b82f6',
    color: '#fff',
  },
  btnSecondary: {
    background: '#f3f4f6',
    color: '#374151',
  },
  btnDanger: {
    background: '#fee2e2',
    color: '#dc2626',
  },
  btnDisabled: {
    opacity: 0.5,
    cursor: 'not-allowed',
  },
};

export const PartnerCard: React.FC<PartnerCardProps> = ({
  partner,
  isSelected = false,
  onSelect,
  onEdit,
  onDelete,
  onTestConnection,
  onDiscover,
  onSwitchEnvironment,
  testingConnection = false,
  discovering = false,
}) => {
  const statusColor = getPartnerStatusColor(partner);
  const roleConfig = OCPI_ROLES.find(r => r.id === partner.role);

  const getEnvStatusColor = (env: OCPIEnvironment): string => {
    if (!env.lastTestResult) return '#9ca3af';
    if (env.lastTestResult === 'success') return '#10b981';
    if (env.lastTestResult === 'failed') return '#ef4444';
    return '#f59e0b';
  };

  const handleCardClick = (e: React.MouseEvent) => {
    // Ne pas sélectionner si on clique sur un bouton
    if ((e.target as HTMLElement).tagName === 'BUTTON') return;
    onSelect?.();
  };

  return (
    <div
      style={{
        ...styles.card,
        ...(isSelected ? styles.cardSelected : {}),
      }}
      onClick={handleCardClick}
    >
      {/* Header */}
      <div style={styles.header}>
        <div style={styles.titleRow}>
          <div
            style={{
              ...styles.statusDot,
              background: statusColor === 'green' ? '#10b981' :
                          statusColor === 'yellow' ? '#f59e0b' : '#ef4444',
            }}
          />
          <h3 style={styles.name}>{partner.name}</h3>
          <span
            style={{
              ...styles.roleBadge,
              background: roleConfig?.color || '#6b7280',
            }}
          >
            {partner.role}
          </span>
        </div>
        <span style={{ fontSize: 12, color: '#9ca3af' }}>
          OCPI {partner.ocpiVersion}
        </span>
      </div>

      {/* Info */}
      <div style={styles.info}>
        <div style={styles.infoItem}>
          <span style={{ fontWeight: 500 }}>{partner.countryCode}-{partner.partyId}</span>
        </div>
        {partner.code && (
          <div style={styles.infoItem}>
            <span>Code: {partner.code}</span>
          </div>
        )}
        {partner.endpoints && Object.keys(partner.endpoints).length > 0 && (
          <div style={styles.infoItem}>
            <span>{Object.keys(partner.endpoints).length} endpoints</span>
          </div>
        )}
      </div>

      {/* Environnements */}
      <div style={styles.envSection}>
        <div style={styles.envLabel}>Environnements</div>
        <div style={styles.envList}>
          {partner.environments.map(env => (
            <div
              key={env.id}
              style={{
                ...styles.envBadge,
                ...(env.isActive ? styles.envBadgeActive : styles.envBadgeInactive),
              }}
              onClick={(e) => {
                e.stopPropagation();
                if (!env.isActive) onSwitchEnvironment?.(env.id);
              }}
              title={env.lastTestError || `Cliquer pour activer ${env.name}`}
            >
              <span
                style={{
                  ...styles.envStatus,
                  background: getEnvStatusColor(env),
                }}
              />
              <span>{env.name}</span>
              {env.lastTestResult === 'success' && <span>OK</span>}
              {env.lastTestResult === 'failed' && <span>KO</span>}
            </div>
          ))}
        </div>
      </div>

      {/* Modules */}
      <div style={styles.modulesSection}>
        <div style={styles.envLabel}>Modules</div>
        <div style={styles.modulesList}>
          {partner.modules.map(mod => (
            <span key={mod} style={styles.moduleBadge}>
              {mod}
            </span>
          ))}
        </div>
      </div>

      {/* Actions */}
      <div style={styles.actions}>
        <button
          style={{
            ...styles.btn,
            ...styles.btnSecondary,
            ...(discovering ? styles.btnDisabled : {}),
          }}
          onClick={(e) => {
            e.stopPropagation();
            onDiscover?.();
          }}
          disabled={discovering}
        >
          {discovering ? 'Discovery...' : 'Discover'}
        </button>
        <button
          style={{
            ...styles.btn,
            ...styles.btnSecondary,
            ...(testingConnection ? styles.btnDisabled : {}),
          }}
          onClick={(e) => {
            e.stopPropagation();
            onTestConnection?.();
          }}
          disabled={testingConnection}
        >
          {testingConnection ? 'Testing...' : 'Tester'}
        </button>
        <button
          style={{ ...styles.btn, ...styles.btnSecondary }}
          onClick={(e) => {
            e.stopPropagation();
            onEdit?.();
          }}
        >
          Editer
        </button>
        <button
          style={{ ...styles.btn, ...styles.btnDanger }}
          onClick={(e) => {
            e.stopPropagation();
            if (confirm(`Supprimer le partenaire ${partner.name} ?`)) {
              onDelete?.();
            }
          }}
        >
          Suppr.
        </button>
      </div>
    </div>
  );
};

export default PartnerCard;
