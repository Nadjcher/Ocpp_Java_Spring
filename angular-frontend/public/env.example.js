(function(window) {
    window['env'] = window['env'] || {};
    window['env']['authentication'] = {
        authorizeUrl: 'cognito-url-example',
        identityProviders: [
            {
                clientId: 'client-id-placeholder',
                displayedName: 'Entra ID',
                id: 'AadLift',
                userType: 'INTERNAL',
            },
            {
                clientId: 'client-id-placeholder',
                displayedName: 'Total Connect BTB',
                id: 'GigyaB2BP',
                userType: 'EXTERNAL',
            },
        ],
        redirectUri: 'http://localhost:4200/login/callback',
    }
    window['env']['portalSdkUrl'] = 'https://evplatform.evcharge-test.totalenergies.com/sdk/index.iife.js';
})(this);
