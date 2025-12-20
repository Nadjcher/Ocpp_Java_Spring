/**
 * PartnersList - Liste des partenaires OCPI avec filtres
 */

import React, { useEffect, useState } from 'react';
import { useShallow } from 'zustand/react/shallow';
import { useOCPIStore } from '@/store/ocpiStore';
import { PartnerCard } from './PartnerCard';
import { PartnerForm } from './PartnerForm';
import { OCPIRole, OCPIVersion, OCPI_ROLES } from './types';

const styles: Record<string, React.CSSProperties> = {
  container: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 20,
    padding: '0 4px',
  },
  title: {
    fontSize: 20,
    fontWeight: 600,
    color: '#111827',
    margin: 0,
  },
  subtitle: {
    fontSize: 13,
    color: '#6b7280',
    marginTop: 4,
  },
  addBtn: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '10px 16px',
    background: '#3b82f6',
    color: '#fff',
    border: 'none',
    borderRadius: 6,
    fontSize: 14,
    fontWeight: 500,
    cursor: 'pointer',
    transition: 'background 0.15s ease',
  },
  filtersBar: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    marginBottom: 16,
    padding: 12,
    background: '#f9fafb',
    borderRadius: 8,
  },
  searchInput: {
    flex: 1,
    padding: '8px 12px',
    border: '1px solid #e5e7eb',
    borderRadius: 6,
    fontSize: 14,
    outline: 'none',
    transition: 'border-color 0.15s ease',
  },
  filterGroup: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  filterLabel: {
    fontSize: 12,
    color: '#6b7280',
    fontWeight: 500,
  },
  filterSelect: {
    padding: '6px 10px',
    border: '1px solid #e5e7eb',
    borderRadius: 4,
    fontSize: 13,
    background: '#fff',
    cursor: 'pointer',
  },
  filterChip: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 4,
    padding: '4px 10px',
    borderRadius: 16,
    fontSize: 12,
    fontWeight: 500,
    cursor: 'pointer',
    transition: 'all 0.15s ease',
  },
  filterChipActive: {
    background: '#dbeafe',
    color: '#1d4ed8',
  },
  filterChipInactive: {
    background: '#f3f4f6',
    color: '#6b7280',
  },
  clearFilters: {
    fontSize: 12,
    color: '#6b7280',
    cursor: 'pointer',
    textDecoration: 'underline',
  },
  list: {
    flex: 1,
    overflowY: 'auto',
    padding: '0 4px',
  },
  emptyState: {
    textAlign: 'center',
    padding: 40,
    color: '#6b7280',
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: 500,
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
    marginBottom: 16,
  },
  stats: {
    display: 'flex',
    gap: 24,
    padding: '12px 16px',
    background: '#f9fafb',
    borderRadius: 8,
    marginBottom: 16,
  },
  statItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  statValue: {
    fontSize: 20,
    fontWeight: 600,
    color: '#111827',
  },
  statLabel: {
    fontSize: 12,
    color: '#6b7280',
  },
  loadingOverlay: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 40,
    color: '#6b7280',
  },
};

export const PartnersList: React.FC = () => {
  const {
    partners,
    filters,
    selectedPartnerId,
    loadingPartners,
    modalOpen,
    editingPartner,
    fetchPartners,
    selectPartner,
    setFilters,
    openModal,
    closeModal,
    deletePartner,
    testPartnerConnection,
    discoverPartnerEndpoints,
    switchPartnerEnvironment,
    getFilteredPartners,
  } = useOCPIStore(
    useShallow((state) => ({
      partners: state.partners,
      filters: state.filters,
      selectedPartnerId: state.selectedPartnerId,
      loadingPartners: state.loadingPartners,
      modalOpen: state.modalOpen,
      editingPartner: state.editingPartner,
      fetchPartners: state.fetchPartners,
      selectPartner: state.selectPartner,
      setFilters: state.setFilters,
      openModal: state.openModal,
      closeModal: state.closeModal,
      deletePartner: state.deletePartner,
      testPartnerConnection: state.testPartnerConnection,
      discoverPartnerEndpoints: state.discoverPartnerEndpoints,
      switchPartnerEnvironment: state.switchPartnerEnvironment,
      getFilteredPartners: state.getFilteredPartners,
    }))
  );

  const [testingPartner, setTestingPartner] = useState<string | null>(null);
  const [discoveringPartner, setDiscoveringPartner] = useState<string | null>(null);

  // Charger les partenaires au montage
  useEffect(() => {
    fetchPartners();
  }, [fetchPartners]);

  const filteredPartners = getFilteredPartners();
  const { search, roles, versions } = filters.partners;

  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFilters({ partners: { ...filters.partners, search: e.target.value } });
  };

  const toggleRoleFilter = (role: OCPIRole) => {
    const newRoles = roles.includes(role)
      ? roles.filter(r => r !== role)
      : [...roles, role];
    setFilters({ partners: { ...filters.partners, roles: newRoles } });
  };

  const handleVersionChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const value = e.target.value;
    const newVersions = value ? [value as OCPIVersion] : [];
    setFilters({ partners: { ...filters.partners, versions: newVersions } });
  };

  const clearFilters = () => {
    setFilters({ partners: { search: '', roles: [], versions: [] } });
  };

  const handleTestConnection = async (partnerId: string) => {
    setTestingPartner(partnerId);
    try {
      const result = await testPartnerConnection(partnerId);
      if (!result.success) {
        alert(`Echec connexion: ${result.error}`);
      }
    } finally {
      setTestingPartner(null);
    }
  };

  const handleDiscover = async (partnerId: string) => {
    setDiscoveringPartner(partnerId);
    try {
      const result = await discoverPartnerEndpoints(partnerId);
      if (result.success) {
        alert(`Discovery OK: ${Object.keys(result.endpoints || {}).length} endpoints trouvés`);
      } else {
        alert(`Echec discovery: ${result.error}`);
      }
    } finally {
      setDiscoveringPartner(null);
    }
  };

  const hasFilters = search || roles.length > 0 || versions.length > 0;

  // Stats
  const activePartners = partners.filter(p => {
    const env = p.environments.find(e => e.id === p.activeEnvironmentId);
    return env?.lastTestResult === 'success';
  });
  const cpoCount = partners.filter(p => p.role === 'CPO').length;
  const mspCount = partners.filter(p => p.role === 'MSP').length;

  return (
    <div style={styles.container}>
      {/* Header */}
      <div style={styles.header}>
        <div>
          <h2 style={styles.title}>Partenaires OCPI</h2>
          <div style={styles.subtitle}>
            {partners.length} partenaire{partners.length !== 1 ? 's' : ''} configurés
          </div>
        </div>
        <button
          style={styles.addBtn}
          onClick={() => openModal('partner', null)}
        >
          <span>+</span>
          Nouveau Partenaire
        </button>
      </div>

      {/* Stats */}
      <div style={styles.stats}>
        <div style={styles.statItem}>
          <div style={styles.statValue}>{partners.length}</div>
          <div style={styles.statLabel}>Total</div>
        </div>
        <div style={styles.statItem}>
          <div style={{ ...styles.statValue, color: '#10b981' }}>{activePartners.length}</div>
          <div style={styles.statLabel}>Actifs</div>
        </div>
        <div style={styles.statItem}>
          <div style={{ ...styles.statValue, color: '#3b82f6' }}>{cpoCount}</div>
          <div style={styles.statLabel}>CPO</div>
        </div>
        <div style={styles.statItem}>
          <div style={{ ...styles.statValue, color: '#10b981' }}>{mspCount}</div>
          <div style={styles.statLabel}>MSP</div>
        </div>
      </div>

      {/* Filtres */}
      <div style={styles.filtersBar}>
        <input
          type="text"
          placeholder="Rechercher par nom, code, pays..."
          style={styles.searchInput}
          value={search}
          onChange={handleSearchChange}
        />

        <div style={styles.filterGroup}>
          <span style={styles.filterLabel}>Rôle:</span>
          {OCPI_ROLES.map(role => (
            <span
              key={role.id}
              style={{
                ...styles.filterChip,
                ...(roles.includes(role.id) ? styles.filterChipActive : styles.filterChipInactive),
              }}
              onClick={() => toggleRoleFilter(role.id)}
            >
              {role.label}
            </span>
          ))}
        </div>

        <div style={styles.filterGroup}>
          <span style={styles.filterLabel}>Version:</span>
          <select
            style={styles.filterSelect}
            value={versions[0] || ''}
            onChange={handleVersionChange}
          >
            <option value="">Toutes</option>
            <option value="2.1.1">2.1.1</option>
            <option value="2.2">2.2</option>
            <option value="2.2.1">2.2.1</option>
          </select>
        </div>

        {hasFilters && (
          <span style={styles.clearFilters} onClick={clearFilters}>
            Effacer filtres
          </span>
        )}
      </div>

      {/* Liste */}
      <div style={styles.list}>
        {loadingPartners ? (
          <div style={styles.loadingOverlay}>
            Chargement des partenaires...
          </div>
        ) : filteredPartners.length === 0 ? (
          <div style={styles.emptyState}>
            <div style={styles.emptyTitle}>
              {hasFilters ? 'Aucun partenaire trouvé' : 'Aucun partenaire'}
            </div>
            <div style={styles.emptyText}>
              {hasFilters
                ? 'Essayez de modifier vos filtres'
                : 'Commencez par ajouter un partenaire OCPI'}
            </div>
            {!hasFilters && (
              <button
                style={styles.addBtn}
                onClick={() => openModal('partner', null)}
              >
                + Ajouter un partenaire
              </button>
            )}
          </div>
        ) : (
          filteredPartners.map(partner => (
            <PartnerCard
              key={partner.id}
              partner={partner}
              isSelected={partner.id === selectedPartnerId}
              onSelect={() => selectPartner(partner.id)}
              onEdit={() => openModal('partner', partner)}
              onDelete={() => deletePartner(partner.id)}
              onTestConnection={() => handleTestConnection(partner.id)}
              onDiscover={() => handleDiscover(partner.id)}
              onSwitchEnvironment={(envId) => switchPartnerEnvironment(partner.id, envId)}
              testingConnection={testingPartner === partner.id}
              discovering={discoveringPartner === partner.id}
            />
          ))
        )}
      </div>

      {/* Modal Partner Form */}
      {modalOpen === 'partner' && (
        <PartnerForm
          partner={editingPartner}
          onClose={closeModal}
        />
      )}
    </div>
  );
};

export default PartnersList;
