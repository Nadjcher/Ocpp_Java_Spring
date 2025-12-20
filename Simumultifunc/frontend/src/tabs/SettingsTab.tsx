// frontend/src/tabs/SettingsTab.tsx
import React, { useState, useEffect } from 'react';
import { TokenStatusPanel } from '@/components/tte/TokenStatusIndicator';

interface TTEConfig {
  enabled: boolean;
  configured: boolean;
  clientId: string;
  tokenUrl: string;
  apiUrls: {
    test: string;
    pp: string;
  };
}

interface BackendConfig {
  tte: TTEConfig;
  ocppUrls: Record<string, string>;
  serverPort: number;
  profile: string;
}

interface CognitoProfile {
  name: string;
  description: string;
  clientId: string;
  configured: boolean;
}

interface ProfilesResponse {
  profiles: CognitoProfile[];
  activeProfile: string;
}

export default function SettingsTab() {
  const [config, setConfig] = useState<BackendConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tteCredentials, setTteCredentials] = useState({
    clientId: '',
    clientSecret: ''
  });
  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);

  // Profiles state
  const [profiles, setProfiles] = useState<CognitoProfile[]>([]);
  const [activeProfile, setActiveProfile] = useState<string>('default');
  const [switchingProfile, setSwitchingProfile] = useState(false);
  const [showAddProfile, setShowAddProfile] = useState(false);
  const [newProfile, setNewProfile] = useState({
    name: '',
    description: '',
    clientId: '',
    clientSecret: ''
  });

  // Charger la configuration
  useEffect(() => {
    loadConfig();
    loadProfiles();
  }, []);

  const loadConfig = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch('/api/config/tte');
      if (response.ok) {
        const data = await response.json();
        setConfig(data);
      } else {
        // Fallback - créer une config par défaut
        setConfig({
          tte: {
            enabled: true,
            configured: false,
            clientId: '',
            tokenUrl: 'https://tte-pool-prod.auth.eu-central-1.amazoncognito.com/oauth2/token',
            apiUrls: {
              test: 'https://evplatform.evcharge-test.totalenergies.com',
              pp: 'https://evplatform.evcharge-pp.totalenergies.com'
            }
          },
          ocppUrls: {},
          serverPort: 8887,
          profile: 'default'
        });
      }
    } catch (e) {
      setError('Impossible de charger la configuration');
      console.error('Config load error:', e);
    } finally {
      setLoading(false);
    }
  };

  const saveTteConfig = async () => {
    if (!tteCredentials.clientId || !tteCredentials.clientSecret) {
      setSaveMessage('Veuillez remplir tous les champs');
      return;
    }

    setSaving(true);
    setSaveMessage(null);
    try {
      const response = await fetch('/api/config/tte', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(tteCredentials)
      });

      if (response.ok) {
        setSaveMessage('Configuration sauvegardée avec succès');
        setTteCredentials({ clientId: '', clientSecret: '' });
        loadConfig();
      } else {
        setSaveMessage('Erreur lors de la sauvegarde');
      }
    } catch (e) {
      setSaveMessage('Erreur de connexion au backend');
    } finally {
      setSaving(false);
    }
  };

  const testTteConnection = async () => {
    try {
      const response = await fetch('/api/tte/token/refresh', { method: 'POST' });
      if (response.ok) {
        setSaveMessage('Connexion TTE réussie !');
      } else {
        const data = await response.json();
        setSaveMessage(`Erreur TTE: ${data.error || 'Échec connexion'}`);
      }
    } catch (e) {
      setSaveMessage('Erreur de connexion');
    }
  };

  // Profile management functions
  const loadProfiles = async () => {
    try {
      const response = await fetch('/api/config/tte/profiles');
      if (response.ok) {
        const data: ProfilesResponse = await response.json();
        setProfiles(data.profiles);
        setActiveProfile(data.activeProfile);
      }
    } catch (e) {
      console.error('Failed to load profiles:', e);
    }
  };

  const switchProfile = async (profileName: string) => {
    if (profileName === activeProfile) return;

    setSwitchingProfile(true);
    setSaveMessage(null);
    try {
      const response = await fetch(`/api/config/tte/profiles/switch/${profileName}`, {
        method: 'POST'
      });
      const data = await response.json();

      if (data.ok) {
        setActiveProfile(profileName);
        setSaveMessage(`Profil "${profileName}" activé${data.tokenRefreshed ? ' - Token obtenu' : ''}`);
        loadProfiles();
      } else {
        setSaveMessage(`Erreur: ${data.error || 'Échec du changement de profil'}`);
      }
    } catch (e) {
      setSaveMessage('Erreur de connexion');
    } finally {
      setSwitchingProfile(false);
    }
  };

  const addProfile = async () => {
    if (!newProfile.name || !newProfile.clientId || !newProfile.clientSecret) {
      setSaveMessage('Veuillez remplir tous les champs obligatoires');
      return;
    }

    setSaving(true);
    try {
      const response = await fetch('/api/config/tte/profiles', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newProfile)
      });
      const data = await response.json();

      if (data.ok) {
        setSaveMessage(`Profil "${newProfile.name}" créé avec succès`);
        setNewProfile({ name: '', description: '', clientId: '', clientSecret: '' });
        setShowAddProfile(false);
        loadProfiles();
      } else {
        setSaveMessage(`Erreur: ${data.error}`);
      }
    } catch (e) {
      setSaveMessage('Erreur de connexion');
    } finally {
      setSaving(false);
    }
  };

  const deleteProfile = async (profileName: string) => {
    if (profileName === 'default') {
      setSaveMessage('Impossible de supprimer le profil par défaut');
      return;
    }

    if (!confirm(`Supprimer le profil "${profileName}" ?`)) return;

    try {
      const response = await fetch(`/api/config/tte/profiles/${profileName}`, {
        method: 'DELETE'
      });
      const data = await response.json();

      if (data.ok) {
        setSaveMessage(`Profil "${profileName}" supprimé`);
        loadProfiles();
      } else {
        setSaveMessage(`Erreur: ${data.error}`);
      }
    } catch (e) {
      setSaveMessage('Erreur de connexion');
    }
  };

  if (loading) {
    return (
      <div className="p-6 flex items-center justify-center">
        <div className="animate-spin w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full" />
      </div>
    );
  }

  return (
    <div className="p-6 max-w-4xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-800">Configuration</h1>

      {error && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-lg text-red-700">
          {error}
        </div>
      )}

      {/* TTE Token Status */}
      <div className="bg-white rounded-lg border shadow-sm">
        <div className="p-4 border-b bg-gray-50">
          <h2 className="text-lg font-semibold">Token TTE Cognito</h2>
          <p className="text-sm text-gray-600">
            Authentification pour l'API TotalEnergies (prix, SCP, etc.)
          </p>
        </div>
        <div className="p-4">
          <TokenStatusPanel />
        </div>
      </div>

      {/* Profils Cognito TTE */}
      <div className="bg-white rounded-lg border shadow-sm">
        <div className="p-4 border-b bg-gray-50 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold">Profils Cognito TTE</h2>
            <p className="text-sm text-gray-600">
              Gérer plusieurs jeux de credentials (GPM, tests, production...)
            </p>
          </div>
          <button
            onClick={() => setShowAddProfile(!showAddProfile)}
            className="px-3 py-1.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm flex items-center gap-1"
          >
            {showAddProfile ? '✕ Annuler' : '+ Ajouter'}
          </button>
        </div>
        <div className="p-4 space-y-4">
          {/* Liste des profils */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {profiles.map(profile => (
              <div
                key={profile.name}
                className={`p-3 rounded-lg border-2 transition-all cursor-pointer ${
                  activeProfile === profile.name
                    ? 'border-blue-500 bg-blue-50'
                    : 'border-gray-200 hover:border-gray-300'
                }`}
                onClick={() => switchProfile(profile.name)}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className={`w-3 h-3 rounded-full ${
                      activeProfile === profile.name ? 'bg-blue-500' : 'bg-gray-300'
                    }`} />
                    <span className="font-medium">{profile.name}</span>
                    {activeProfile === profile.name && (
                      <span className="text-xs bg-blue-500 text-white px-2 py-0.5 rounded-full">
                        Actif
                      </span>
                    )}
                  </div>
                  {profile.name !== 'default' && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        deleteProfile(profile.name);
                      }}
                      className="text-red-500 hover:text-red-700 text-sm px-2"
                      title="Supprimer"
                    >
                      ✕
                    </button>
                  )}
                </div>
                <div className="mt-1 text-xs text-gray-500">
                  {profile.description || 'Aucune description'}
                </div>
                <div className="mt-1 flex items-center gap-2">
                  <span className={`w-2 h-2 rounded-full ${profile.configured ? 'bg-green-500' : 'bg-yellow-500'}`} />
                  <span className="text-xs text-gray-600">
                    {profile.configured ? 'Configuré' : 'Non configuré'}
                  </span>
                  {profile.clientId && (
                    <span className="text-xs font-mono text-gray-400">
                      {profile.clientId}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>

          {switchingProfile && (
            <div className="flex items-center gap-2 text-blue-600">
              <div className="animate-spin w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full" />
              <span className="text-sm">Changement de profil...</span>
            </div>
          )}

          {/* Formulaire d'ajout de profil */}
          {showAddProfile && (
            <div className="mt-4 p-4 bg-gray-50 rounded-lg border">
              <h4 className="text-sm font-medium text-gray-700 mb-3">
                Nouveau profil Cognito
              </h4>
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs text-gray-600 mb-1">Nom du profil *</label>
                    <input
                      type="text"
                      value={newProfile.name}
                      onChange={e => setNewProfile(prev => ({ ...prev, name: e.target.value }))}
                      placeholder="gpm, test, prod..."
                      className="w-full px-3 py-2 border rounded-lg text-sm"
                    />
                  </div>
                  <div>
                    <label className="block text-xs text-gray-600 mb-1">Description</label>
                    <input
                      type="text"
                      value={newProfile.description}
                      onChange={e => setNewProfile(prev => ({ ...prev, description: e.target.value }))}
                      placeholder="Tests GPM..."
                      className="w-full px-3 py-2 border rounded-lg text-sm"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs text-gray-600 mb-1">Client ID *</label>
                  <input
                    type="text"
                    value={newProfile.clientId}
                    onChange={e => setNewProfile(prev => ({ ...prev, clientId: e.target.value }))}
                    placeholder="3tmbtgs4jcdf7f53uedn1eej11"
                    className="w-full px-3 py-2 border rounded-lg text-sm font-mono"
                  />
                </div>
                <div>
                  <label className="block text-xs text-gray-600 mb-1">Client Secret *</label>
                  <input
                    type="password"
                    value={newProfile.clientSecret}
                    onChange={e => setNewProfile(prev => ({ ...prev, clientSecret: e.target.value }))}
                    placeholder="••••••••••••••••"
                    className="w-full px-3 py-2 border rounded-lg text-sm font-mono"
                  />
                </div>
                <div className="flex gap-2 pt-2">
                  <button
                    onClick={addProfile}
                    disabled={saving || !newProfile.name || !newProfile.clientId || !newProfile.clientSecret}
                    className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 text-sm"
                  >
                    {saving ? 'Création...' : 'Créer le profil'}
                  </button>
                  <button
                    onClick={() => {
                      setShowAddProfile(false);
                      setNewProfile({ name: '', description: '', clientId: '', clientSecret: '' });
                    }}
                    className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 text-sm"
                  >
                    Annuler
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* TTE Configuration */}
      <div className="bg-white rounded-lg border shadow-sm">
        <div className="p-4 border-b bg-gray-50">
          <h2 className="text-lg font-semibold">Configuration TTE</h2>
          <p className="text-sm text-gray-600">
            Identifiants Cognito pour l'authentification OAuth2
          </p>
        </div>
        <div className="p-4 space-y-4">
          {/* Status actuel */}
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span className="text-gray-600">Statut:</span>
              <span className={`ml-2 font-medium ${config?.tte?.configured ? 'text-green-600' : 'text-yellow-600'}`}>
                {config?.tte?.configured ? 'Configuré' : 'Non configuré'}
              </span>
            </div>
            <div>
              <span className="text-gray-600">Activé:</span>
              <span className={`ml-2 font-medium ${config?.tte?.enabled ? 'text-green-600' : 'text-red-600'}`}>
                {config?.tte?.enabled ? 'Oui' : 'Non'}
              </span>
            </div>
            <div>
              <span className="text-gray-600">Profil actif:</span>
              <span className="ml-2 font-medium">{config?.profile || 'default'}</span>
            </div>
            <div>
              <span className="text-gray-600">Client ID:</span>
              <span className="ml-2 font-mono text-xs">
                {config?.tte?.clientId ? `${config.tte.clientId.substring(0, 8)}...` : 'Non défini'}
              </span>
            </div>
          </div>

          {/* URLs TTE */}
          <div className="mt-4 p-3 bg-gray-50 rounded-lg">
            <h4 className="text-sm font-medium text-gray-700 mb-2">URLs TTE API</h4>
            <div className="space-y-1 text-xs font-mono">
              <div>
                <span className="text-gray-500">TEST:</span>{' '}
                <span className="text-blue-600">{config?.tte?.apiUrls?.test || 'N/A'}</span>
              </div>
              <div>
                <span className="text-gray-500">PP:</span>{' '}
                <span className="text-blue-600">{config?.tte?.apiUrls?.pp || 'N/A'}</span>
              </div>
            </div>
          </div>

          {/* Formulaire de configuration */}
          <div className="mt-6 pt-4 border-t">
            <h4 className="text-sm font-medium text-gray-700 mb-3">
              Modifier les identifiants Cognito
            </h4>
            <div className="space-y-3">
              <div>
                <label className="block text-sm text-gray-600 mb-1">Client ID</label>
                <input
                  type="text"
                  value={tteCredentials.clientId}
                  onChange={e => setTteCredentials(prev => ({ ...prev, clientId: e.target.value }))}
                  placeholder="3tmbtgs4jcdf7f53uedn1eej11"
                  className="w-full px-3 py-2 border rounded-lg text-sm font-mono"
                />
              </div>
              <div>
                <label className="block text-sm text-gray-600 mb-1">Client Secret</label>
                <input
                  type="password"
                  value={tteCredentials.clientSecret}
                  onChange={e => setTteCredentials(prev => ({ ...prev, clientSecret: e.target.value }))}
                  placeholder="••••••••••••••••"
                  className="w-full px-3 py-2 border rounded-lg text-sm font-mono"
                />
              </div>
              <div className="flex gap-2">
                <button
                  onClick={saveTteConfig}
                  disabled={saving}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 text-sm"
                >
                  {saving ? 'Sauvegarde...' : 'Sauvegarder'}
                </button>
                <button
                  onClick={testTteConnection}
                  className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 text-sm"
                >
                  Tester la connexion
                </button>
              </div>
              {saveMessage && (
                <div className={`text-sm ${saveMessage.includes('succès') || saveMessage.includes('réussie') ? 'text-green-600' : 'text-red-600'}`}>
                  {saveMessage}
                </div>
              )}
            </div>
          </div>

          {/* Note importante */}
          <div className="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
            <h4 className="text-sm font-medium text-yellow-800">Note importante</h4>
            <p className="text-xs text-yellow-700 mt-1">
              Les identifiants sont également configurables via variables d'environnement:<br/>
              <code className="bg-yellow-100 px-1">TTE_CLIENT_ID</code> et{' '}
              <code className="bg-yellow-100 px-1">TTE_CLIENT_SECRET</code><br/>
              Le backend doit être démarré avec le profil <code className="bg-yellow-100 px-1">dev</code> pour utiliser les valeurs par défaut.
            </p>
          </div>
        </div>
      </div>

      {/* OCPP URLs */}
      <div className="bg-white rounded-lg border shadow-sm">
        <div className="p-4 border-b bg-gray-50">
          <h2 className="text-lg font-semibold">URLs OCPP</h2>
          <p className="text-sm text-gray-600">
            Points de connexion WebSocket pour les différents environnements
          </p>
        </div>
        <div className="p-4">
          <div className="space-y-2 text-sm">
            {Object.entries(config?.ocppUrls || {}).map(([env, url]) => (
              <div key={env} className="flex items-center gap-2">
                <span className="w-16 font-medium uppercase text-gray-600">{env}:</span>
                <code className="flex-1 px-2 py-1 bg-gray-100 rounded text-xs">{url}</code>
              </div>
            ))}
            {Object.keys(config?.ocppUrls || {}).length === 0 && (
              <p className="text-gray-500">Aucune URL configurée</p>
            )}
          </div>
        </div>
      </div>

      {/* Fonctionnalités TTE */}
      <div className="bg-white rounded-lg border shadow-sm">
        <div className="p-4 border-b bg-gray-50">
          <h2 className="text-lg font-semibold">Fonctionnalités TTE</h2>
        </div>
        <div className="p-4">
          <div className="grid grid-cols-2 gap-4">
            <FeatureCard
              title="Récupération des prix"
              description="Tarifs dynamiques depuis l'API TTE"
              endpoint="/api/tte/prices"
              enabled={config?.tte?.configured}
            />
            <FeatureCard
              title="Envoi SCP"
              description="Smart Charging Profiles via Central Task"
              endpoint="/api/smart-charging/central-task/set-profile"
              enabled={config?.tte?.configured}
            />
            <FeatureCard
              title="GetCompositeSchedule"
              description="Récupération du planning de charge"
              endpoint="/api/smart-charging/central-task/get-composite"
              enabled={config?.tte?.configured}
            />
            <FeatureCard
              title="Remote Start/Stop"
              description="Commandes à distance via TTE"
              endpoint="/api/tte/remote-start"
              enabled={config?.tte?.configured}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function FeatureCard({
  title,
  description,
  endpoint,
  enabled
}: {
  title: string;
  description: string;
  endpoint: string;
  enabled?: boolean;
}) {
  return (
    <div className={`p-3 rounded-lg border ${enabled ? 'bg-green-50 border-green-200' : 'bg-gray-50 border-gray-200'}`}>
      <div className="flex items-center gap-2">
        <span className={`w-2 h-2 rounded-full ${enabled ? 'bg-green-500' : 'bg-gray-400'}`} />
        <span className="font-medium text-sm">{title}</span>
      </div>
      <p className="text-xs text-gray-600 mt-1">{description}</p>
      <code className="text-[10px] text-gray-400 mt-1 block">{endpoint}</code>
    </div>
  );
}
