// Configuration for different environments
const CONFIG = {
  // Development environment (localhost)
  development: {
    API_BASE: 'http://localhost:8080',
    GOOGLE_CLIENT_ID: '10217611478-r6n5miarsidp97u82kt9fcqcle9ftcb3.apps.googleusercontent.com'
  },
  
  // Production environment (GitHub Pages)
  production: {
    API_BASE: 'https://27e9c817fd1cfac449afcddb3327341a.serveo.net', // Temporary: Update this when you deploy backend
    GOOGLE_CLIENT_ID: '10217611478-r6n5miarsidp97u82kt9fcqcle9ftcb3.apps.googleusercontent.com'
  }
};

// Detect environment
const isProduction = window.location.hostname !== 'localhost' && 
                    window.location.hostname !== '127.0.0.1';

// Export current configuration
const currentConfig = isProduction ? CONFIG.production : CONFIG.development;

// Make config available globally
window.APP_CONFIG = currentConfig; 