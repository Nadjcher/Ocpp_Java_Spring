// =============================================================================
// MongoDB Initialization Script for EVSE Simulator
// =============================================================================
// This script runs automatically when MongoDB container starts for the first time

// Switch to evse_simulator database
db = db.getSiblingDB('evse_simulator');

// Create application user with readWrite access
db.createUser({
    user: 'evse',
    pwd: 'evse_secret',
    roles: [
        { role: 'readWrite', db: 'evse_simulator' }
    ]
});

print('Created user: evse');

// =============================================================================
// Create Collections with Schema Validation
// =============================================================================

// Sessions Collection
db.createCollection('sessions', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['cpId'],
            properties: {
                _id: { bsonType: 'string' },
                title: { bsonType: 'string' },
                cpId: { bsonType: 'string', description: 'Charge Point ID - required' },
                url: { bsonType: 'string' },
                bearerToken: { bsonType: 'string' },
                state: { enum: ['DISCONNECTED', 'AVAILABLE', 'PREPARING', 'CHARGING', 'SUSPENDED_EV', 'SUSPENDED_EVSE', 'FINISHING', 'RESERVED', 'UNAVAILABLE', 'FAULTED'] },
                connected: { bsonType: 'bool' },
                authorized: { bsonType: 'bool' },
                soc: { bsonType: 'double', minimum: 0, maximum: 100 },
                targetSoc: { bsonType: 'double', minimum: 0, maximum: 100 },
                currentPowerKw: { bsonType: 'double', minimum: 0 },
                maxPowerKw: { bsonType: 'double', minimum: 0 },
                energyDeliveredKwh: { bsonType: 'double', minimum: 0 },
                voltage: { bsonType: 'double' },
                currentA: { bsonType: 'double' },
                chargerType: { enum: ['AC_MONO', 'AC_BI', 'AC_TRI', 'DC'] },
                createdAt: { bsonType: 'date' },
                updatedAt: { bsonType: 'date' }
            }
        }
    },
    validationLevel: 'moderate',
    validationAction: 'warn'
});

print('Created collection: sessions');

// Create indexes for sessions
db.sessions.createIndex({ cpId: 1 });
db.sessions.createIndex({ state: 1 });
db.sessions.createIndex({ connected: 1 });
db.sessions.createIndex({ updatedAt: -1 });
db.sessions.createIndex({ createdAt: -1 });

print('Created indexes for sessions');

// Vehicle Profiles Collection
db.createCollection('vehicleProfiles', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['name'],
            properties: {
                _id: { bsonType: 'string' },
                brand: { bsonType: 'string' },
                model: { bsonType: 'string' },
                name: { bsonType: 'string' },
                batteryCapacityKwh: { bsonType: 'double', minimum: 0 },
                maxDcPowerKw: { bsonType: 'double', minimum: 0 },
                maxAcPowerKw: { bsonType: 'double', minimum: 0 },
                maxAcPhases: { bsonType: 'int', minimum: 1, maximum: 3 }
            }
        }
    },
    validationLevel: 'moderate',
    validationAction: 'warn'
});

print('Created collection: vehicleProfiles');

db.vehicleProfiles.createIndex({ brand: 1 });
db.vehicleProfiles.createIndex({ name: 1 });
db.vehicleProfiles.createIndex({ active: 1 });

// Insert default vehicle profiles
db.vehicleProfiles.insertMany([
    {
        _id: 'tesla-model-3-lr',
        brand: 'Tesla',
        model: 'Model 3 Long Range',
        name: 'Tesla Model 3 LR',
        displayName: 'Tesla Model 3 LR',
        batteryCapacityKwh: 78.0,
        maxDcPowerKw: 250.0,
        maxAcPowerKw: 11.0,
        maxAcPhases: 3,
        maxAcCurrentA: 16.0,
        maxDcCurrentA: 500.0,
        efficiencyAc: 0.90,
        efficiencyDc: 0.92,
        active: true,
        createdAt: new Date(),
        updatedAt: new Date()
    },
    {
        _id: 'renault-zoe-r135',
        brand: 'Renault',
        model: 'Zoe R135',
        name: 'Renault Zoe R135',
        displayName: 'Renault Zoe R135',
        batteryCapacityKwh: 52.0,
        maxDcPowerKw: 100.0,
        maxAcPowerKw: 22.0,
        maxAcPhases: 3,
        maxAcCurrentA: 32.0,
        maxDcCurrentA: 200.0,
        efficiencyAc: 0.90,
        efficiencyDc: 0.92,
        active: true,
        createdAt: new Date(),
        updatedAt: new Date()
    },
    {
        _id: 'hyundai-ioniq5-77',
        brand: 'Hyundai',
        model: 'Ioniq 5',
        name: 'Hyundai Ioniq 5 77kWh',
        displayName: 'Hyundai Ioniq 5',
        batteryCapacityKwh: 77.4,
        maxDcPowerKw: 233.0,
        maxAcPowerKw: 11.0,
        maxAcPhases: 3,
        maxAcCurrentA: 16.0,
        maxDcCurrentA: 500.0,
        efficiencyAc: 0.90,
        efficiencyDc: 0.92,
        active: true,
        createdAt: new Date(),
        updatedAt: new Date()
    },
    {
        _id: 'generic',
        brand: 'Generic',
        model: 'EV',
        name: 'Generic EV',
        displayName: 'Generic EV',
        batteryCapacityKwh: 60.0,
        maxDcPowerKw: 150.0,
        maxAcPowerKw: 11.0,
        maxAcPhases: 3,
        maxAcCurrentA: 16.0,
        maxDcCurrentA: 300.0,
        efficiencyAc: 0.90,
        efficiencyDc: 0.92,
        active: true,
        createdAt: new Date(),
        updatedAt: new Date()
    }
]);

print('Inserted default vehicle profiles');

// TNR Scenarios Collection
db.createCollection('tnrScenarios');
db.tnrScenarios.createIndex({ name: 1 });
db.tnrScenarios.createIndex({ category: 1 });
db.tnrScenarios.createIndex({ status: 1 });
db.tnrScenarios.createIndex({ active: 1 });

print('Created collection: tnrScenarios');

// TNR Executions Collection
db.createCollection('tnrExecutions');
db.tnrExecutions.createIndex({ scenarioId: 1 });
db.tnrExecutions.createIndex({ status: 1 });
db.tnrExecutions.createIndex({ executedAt: -1 });

print('Created collection: tnrExecutions');

// Scheduled Tasks Collection
db.createCollection('scheduledTasks');
db.scheduledTasks.createIndex({ enabled: 1 });
db.scheduledTasks.createIndex({ nextRunAt: 1 });

print('Created collection: scheduledTasks');

// Task Executions Collection
db.createCollection('taskExecutions');
db.taskExecutions.createIndex({ taskId: 1 });
db.taskExecutions.createIndex({ status: 1 });
db.taskExecutions.createIndex({ startedAt: -1 });

print('Created collection: taskExecutions');

// OCPI Partners Collection
db.createCollection('ocpiPartners');
db.ocpiPartners.createIndex({ name: 1 });
db.ocpiPartners.createIndex({ active: 1 });

print('Created collection: ocpiPartners');

print('MongoDB initialization completed successfully!');
