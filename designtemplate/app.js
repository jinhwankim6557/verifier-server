document.addEventListener('DOMContentLoaded', () => {
    // Screens
    const screenMetadataView = document.getElementById('screen-issuer-metadata-view');
    const screenMetadataEdit = document.getElementById('screen-issuer-metadata-edit');
    const screenConfigList = document.getElementById('screen-credential-config-list');
    const screenRegister = document.getElementById('screen-credential-register');
    const screenProperty = document.getElementById('screen-property');
    
    // Help Panels
    const helpMetadata = document.getElementById('help-content-metadata');
    const helpMetadataEdit = document.getElementById('help-content-metadata-edit');
    const helpConfig = document.getElementById('help-content-config');
    const helpRegister = document.getElementById('help-content-register');
    const helpProperty = document.getElementById('help-content-property');

    // Sidebar Links
    const menuMetadataLink = document.getElementById('menu-issuer-metadata-link');
    const menuConfigLink = document.getElementById('menu-credential-config-link');
    const menuPropertyLink = document.getElementById('menu-property-link');

    const allScreens = [screenMetadataView, screenMetadataEdit, screenConfigList, screenRegister, screenProperty];
    const allHelps = [helpMetadata, helpMetadataEdit, helpConfig, helpRegister, helpProperty];

    function showScreen(screenId, helpId) {
        allScreens.forEach(s => s.classList.add('hidden'));
        allHelps.forEach(h => h.classList.add('hidden'));
        
        document.getElementById(screenId).classList.remove('hidden');
        document.getElementById(helpId).classList.remove('hidden');

        // Update active menu state
        [menuMetadataLink, menuConfigLink, menuPropertyLink].forEach(l => l.classList.remove('active'));
        if (screenId.includes('metadata')) {
            menuMetadataLink.classList.add('active');
        } else if (screenId.includes('credential') || screenId.includes('config')) {
            menuConfigLink.classList.add('active');
        } else if (screenId.includes('property')) {
            menuPropertyLink.classList.add('active');
        }
    }

    // Issuer Metadata Interaction
    document.getElementById('btn-edit-issuer-metadata').addEventListener('click', () => {
        showScreen('screen-issuer-metadata-edit', 'help-content-metadata-edit');
    });

    document.getElementById('btn-save-issuer-metadata').addEventListener('click', () => {
        showScreen('screen-issuer-metadata-view', 'help-content-metadata');
    });

    document.getElementById('btn-cancel-edit-metadata').addEventListener('click', () => {
        showScreen('screen-issuer-metadata-view', 'help-content-metadata');
    });

    // Sidebar Navigation
    menuConfigLink.addEventListener('click', () => {
        showScreen('screen-credential-config-list', 'help-content-config');
    });

    menuMetadataLink.addEventListener('click', () => {
        showScreen('screen-issuer-metadata-view', 'help-content-metadata');
    });

    menuPropertyLink.addEventListener('click', () => {
        showScreen('screen-property', 'help-content-property');
    });

    // Credential Config Interaction
    document.getElementById('btn-add-credential').addEventListener('click', () => {
        showScreen('screen-credential-register', 'help-content-register');
    });

    document.getElementById('link-credential-detail').addEventListener('click', (e) => {
        e.preventDefault();
        showScreen('screen-credential-register', 'help-content-register');
    });

    document.getElementById('btn-save-credential').addEventListener('click', () => {
        showScreen('screen-credential-config-list', 'help-content-config');
    });

    // Property Interaction
    document.getElementById('btn-save-property').addEventListener('click', () => {
        alert('Property saved successfully!');
    });
});
