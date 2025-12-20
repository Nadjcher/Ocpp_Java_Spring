/**
 * QuickTestConfig - Configuration des paramètres d'un test rapide OCPI
 */

import React, { useState } from 'react';
import {
  OCPIQuickTest,
  OCPIPartner,
  QuickTestParam,
  OCPIValidation,
} from './types';

interface QuickTestConfigProps {
  test: OCPIQuickTest;
  partner?: OCPIPartner | null;
  onChange: (test: OCPIQuickTest) => void;
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: 8,
    overflow: 'hidden',
  },
  header: {
    padding: 16,
    borderBottom: '1px solid #e5e7eb',
    background: '#f9fafb',
  },
  title: {
    fontSize: 14,
    fontWeight: 600,
    color: '#374151',
    margin: 0,
  },
  content: {
    padding: 16,
  },
  section: {
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: 600,
    color: '#6b7280',
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
    marginBottom: 12,
  },
  field: {
    marginBottom: 12,
  },
  label: {
    display: 'block',
    fontSize: 13,
    fontWeight: 500,
    color: '#374151',
    marginBottom: 4,
  },
  input: {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
    outline: 'none',
    transition: 'border-color 0.15s ease',
  },
  select: {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
    background: '#fff',
  },
  textarea: {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 13,
    fontFamily: 'ui-monospace, monospace',
    minHeight: 100,
    resize: 'vertical' as const,
  },
  paramRow: {
    display: 'grid',
    gridTemplateColumns: '1fr 2fr auto',
    gap: 8,
    marginBottom: 8,
    alignItems: 'center',
  },
  paramInput: {
    padding: '6px 10px',
    border: '1px solid #d1d5db',
    borderRadius: 4,
    fontSize: 13,
  },
  addBtn: {
    padding: '6px 12px',
    background: '#f3f4f6',
    border: '1px dashed #d1d5db',
    borderRadius: 4,
    fontSize: 12,
    color: '#6b7280',
    cursor: 'pointer',
    transition: 'all 0.15s ease',
  },
  removeBtn: {
    padding: '4px 8px',
    background: '#fee2e2',
    border: 'none',
    borderRadius: 4,
    fontSize: 12,
    color: '#dc2626',
    cursor: 'pointer',
  },
  validationRow: {
    display: 'grid',
    gridTemplateColumns: '120px 100px 1fr auto',
    gap: 8,
    marginBottom: 8,
    alignItems: 'center',
  },
  badge: {
    display: 'inline-block',
    padding: '2px 8px',
    borderRadius: 4,
    fontSize: 11,
    fontWeight: 500,
  },
  pathPreview: {
    padding: 12,
    background: '#1e293b',
    borderRadius: 6,
    fontFamily: 'ui-monospace, monospace',
    fontSize: 13,
    color: '#e2e8f0',
    overflow: 'auto',
  },
  pathVariable: {
    color: '#fbbf24',
    fontWeight: 500,
  },
  tabs: {
    display: 'flex',
    gap: 4,
    borderBottom: '1px solid #e5e7eb',
    padding: '0 16px',
  },
  tab: {
    padding: '10px 16px',
    background: 'transparent',
    border: 'none',
    borderBottom: '2px solid transparent',
    fontSize: 13,
    fontWeight: 500,
    color: '#6b7280',
    cursor: 'pointer',
    transition: 'all 0.15s ease',
  },
  tabActive: {
    color: '#3b82f6',
    borderBottomColor: '#3b82f6',
  },
  infoBox: {
    padding: 12,
    background: '#eff6ff',
    border: '1px solid #bfdbfe',
    borderRadius: 6,
    fontSize: 13,
    color: '#1e40af',
    marginBottom: 16,
  },
};

type ConfigTab = 'params' | 'headers' | 'body' | 'validations';

export const QuickTestConfig: React.FC<QuickTestConfigProps> = ({
  test,
  partner,
  onChange,
}) => {
  const [activeTab, setActiveTab] = useState<ConfigTab>('params');
  const [customHeaders, setCustomHeaders] = useState<Record<string, string>>({});
  const [requestBody, setRequestBody] = useState('');

  // Extract path variables from the path
  const pathVariables = test.path.match(/\{([^}]+)\}/g)?.map(v => v.slice(1, -1)) || [];

  const updateParam = (name: string, value: string) => {
    const params = [...(test.params || [])];
    const existingIdx = params.findIndex(p => p.name === name);

    if (existingIdx >= 0) {
      params[existingIdx] = { ...params[existingIdx], value };
    } else {
      params.push({
        name,
        type: 'path',
        required: true,
        value,
      });
    }

    onChange({ ...test, params });
  };

  const addQueryParam = () => {
    const params = [...(test.params || [])];
    params.push({
      name: '',
      type: 'query',
      required: false,
      value: '',
    });
    onChange({ ...test, params });
  };

  const removeParam = (index: number) => {
    const params = [...(test.params || [])];
    params.splice(index, 1);
    onChange({ ...test, params });
  };

  const addValidation = () => {
    const validations = [...(test.validations || [])];
    validations.push({
      field: '',
      operator: 'equals',
      expected: '',
    });
    onChange({ ...test, validations });
  };

  const updateValidation = (index: number, updates: Partial<OCPIValidation>) => {
    const validations = [...(test.validations || [])];
    validations[index] = { ...validations[index], ...updates };
    onChange({ ...test, validations });
  };

  const removeValidation = (index: number) => {
    const validations = [...(test.validations || [])];
    validations.splice(index, 1);
    onChange({ ...test, validations });
  };

  // Build preview path with filled variables
  const getPreviewPath = () => {
    let path = test.path;
    (test.params || []).forEach(param => {
      if (param.type === 'path' && param.value) {
        path = path.replace(`{${param.name}}`, param.value);
      }
    });
    return path;
  };

  const queryParams = (test.params || []).filter(p => p.type === 'query');

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h3 style={styles.title}>Configuration du Test</h3>
      </div>

      {/* Tabs */}
      <div style={styles.tabs}>
        {(['params', 'headers', 'body', 'validations'] as ConfigTab[]).map((tab) => (
          <button
            key={tab}
            style={{
              ...styles.tab,
              ...(activeTab === tab ? styles.tabActive : {}),
            }}
            onClick={() => setActiveTab(tab)}
          >
            {tab === 'params' && 'Paramètres'}
            {tab === 'headers' && 'Headers'}
            {tab === 'body' && 'Body'}
            {tab === 'validations' && 'Validations'}
          </button>
        ))}
      </div>

      <div style={styles.content}>
        {/* Partner Info */}
        {partner && (
          <div style={styles.infoBox}>
            <strong>Partenaire:</strong> {partner.name} ({partner.role})
            <br />
            <strong>Version:</strong> OCPI {partner.ocpiVersion}
          </div>
        )}

        {/* Path Preview */}
        <div style={styles.section}>
          <div style={styles.sectionTitle}>URL Preview</div>
          <div style={styles.pathPreview}>
            <span style={{ color: '#94a3b8' }}>
              {test.method}
            </span>
            {' '}
            <span>
              {getPreviewPath().split(/(\{[^}]+\})/).map((part, i) => (
                part.startsWith('{') ? (
                  <span key={i} style={styles.pathVariable}>{part}</span>
                ) : (
                  <span key={i}>{part}</span>
                )
              ))}
            </span>
          </div>
        </div>

        {/* Params Tab */}
        {activeTab === 'params' && (
          <>
            {/* Path Variables */}
            {pathVariables.length > 0 && (
              <div style={styles.section}>
                <div style={styles.sectionTitle}>Variables de chemin</div>
                {pathVariables.map((varName) => {
                  const param = (test.params || []).find(p => p.name === varName);
                  return (
                    <div key={varName} style={styles.field}>
                      <label style={styles.label}>
                        {varName}
                        <span style={{ color: '#ef4444', marginLeft: 4 }}>*</span>
                      </label>
                      <input
                        type="text"
                        style={styles.input}
                        value={param?.value || ''}
                        onChange={(e) => updateParam(varName, e.target.value)}
                        placeholder={`Valeur pour {${varName}}`}
                      />
                    </div>
                  );
                })}
              </div>
            )}

            {/* Query Parameters */}
            <div style={styles.section}>
              <div style={styles.sectionTitle}>Paramètres de requête</div>
              {queryParams.map((param, idx) => {
                const actualIdx = (test.params || []).findIndex(p => p === param);
                return (
                  <div key={idx} style={styles.paramRow}>
                    <input
                      type="text"
                      style={styles.paramInput}
                      value={param.name}
                      onChange={(e) => {
                        const params = [...(test.params || [])];
                        params[actualIdx] = { ...param, name: e.target.value };
                        onChange({ ...test, params });
                      }}
                      placeholder="Nom"
                    />
                    <input
                      type="text"
                      style={styles.paramInput}
                      value={param.value || ''}
                      onChange={(e) => {
                        const params = [...(test.params || [])];
                        params[actualIdx] = { ...param, value: e.target.value };
                        onChange({ ...test, params });
                      }}
                      placeholder="Valeur"
                    />
                    <button
                      style={styles.removeBtn}
                      onClick={() => removeParam(actualIdx)}
                    >
                      ✕
                    </button>
                  </div>
                );
              })}
              <button style={styles.addBtn} onClick={addQueryParam}>
                + Ajouter paramètre
              </button>
            </div>
          </>
        )}

        {/* Headers Tab */}
        {activeTab === 'headers' && (
          <div style={styles.section}>
            <div style={styles.sectionTitle}>Headers personnalisés</div>
            <div style={{
              padding: 12,
              background: '#f9fafb',
              borderRadius: 6,
              fontSize: 12,
              color: '#6b7280',
              marginBottom: 16,
            }}>
              Les headers d'authentification (Authorization, X-Token-*) sont ajoutés automatiquement
              selon la configuration du partenaire.
            </div>
            {Object.entries(customHeaders).map(([key, value], idx) => (
              <div key={idx} style={styles.paramRow}>
                <input
                  type="text"
                  style={styles.paramInput}
                  value={key}
                  onChange={(e) => {
                    const newHeaders = { ...customHeaders };
                    delete newHeaders[key];
                    newHeaders[e.target.value] = value;
                    setCustomHeaders(newHeaders);
                  }}
                  placeholder="Header name"
                />
                <input
                  type="text"
                  style={styles.paramInput}
                  value={value}
                  onChange={(e) => {
                    setCustomHeaders({ ...customHeaders, [key]: e.target.value });
                  }}
                  placeholder="Value"
                />
                <button
                  style={styles.removeBtn}
                  onClick={() => {
                    const newHeaders = { ...customHeaders };
                    delete newHeaders[key];
                    setCustomHeaders(newHeaders);
                  }}
                >
                  ✕
                </button>
              </div>
            ))}
            <button
              style={styles.addBtn}
              onClick={() => setCustomHeaders({ ...customHeaders, '': '' })}
            >
              + Ajouter header
            </button>
          </div>
        )}

        {/* Body Tab */}
        {activeTab === 'body' && (
          <div style={styles.section}>
            <div style={styles.sectionTitle}>Corps de la requête (JSON)</div>
            {test.method === 'GET' ? (
              <div style={{
                padding: 12,
                background: '#fef3c7',
                border: '1px solid #fcd34d',
                borderRadius: 6,
                fontSize: 13,
                color: '#92400e',
              }}>
                Les requêtes GET n'ont généralement pas de body.
              </div>
            ) : (
              <>
                <textarea
                  style={styles.textarea}
                  value={requestBody}
                  onChange={(e) => setRequestBody(e.target.value)}
                  placeholder='{\n  "key": "value"\n}'
                />
                <div style={{ fontSize: 11, color: '#6b7280', marginTop: 4 }}>
                  Format JSON valide requis pour POST/PUT/PATCH
                </div>
              </>
            )}
          </div>
        )}

        {/* Validations Tab */}
        {activeTab === 'validations' && (
          <div style={styles.section}>
            <div style={styles.sectionTitle}>Validations de réponse</div>
            <div style={{
              padding: 12,
              background: '#f9fafb',
              borderRadius: 6,
              fontSize: 12,
              color: '#6b7280',
              marginBottom: 16,
            }}>
              Définissez des règles de validation pour vérifier automatiquement la réponse.
              Utilisez la notation dot (ex: data.0.id) pour accéder aux champs imbriqués.
            </div>
            {(test.validations || []).map((validation, idx) => (
              <div key={idx} style={styles.validationRow}>
                <input
                  type="text"
                  style={styles.paramInput}
                  value={validation.field}
                  onChange={(e) => updateValidation(idx, { field: e.target.value })}
                  placeholder="Champ (ex: status_code)"
                />
                <select
                  style={{ ...styles.paramInput, padding: '6px 8px' }}
                  value={validation.operator}
                  onChange={(e) => updateValidation(idx, { operator: e.target.value as any })}
                >
                  <option value="equals">égal</option>
                  <option value="not_equals">différent</option>
                  <option value="contains">contient</option>
                  <option value="not_contains">ne contient pas</option>
                  <option value="exists">existe</option>
                  <option value="not_exists">n'existe pas</option>
                  <option value="greater_than">supérieur à</option>
                  <option value="less_than">inférieur à</option>
                  <option value="matches">regex</option>
                </select>
                <input
                  type="text"
                  style={styles.paramInput}
                  value={validation.expected}
                  onChange={(e) => updateValidation(idx, { expected: e.target.value })}
                  placeholder="Valeur attendue"
                  disabled={['exists', 'not_exists'].includes(validation.operator)}
                />
                <button
                  style={styles.removeBtn}
                  onClick={() => removeValidation(idx)}
                >
                  ✕
                </button>
              </div>
            ))}
            <button style={styles.addBtn} onClick={addValidation}>
              + Ajouter validation
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default QuickTestConfig;
