// Configuration for different environments
const CONFIG = {
  // Development environment (localhost)
  development: {
    API_BASE: 'http://localhost:8080',
    GOOGLE_CLIENT_ID: '10217611478-r6n5miarsidp97u82kt9fcqcle9ftcb3.apps.googleusercontent.com',
    FACEBOOK_APP_ID: 'YOUR_FACEBOOK_APP_ID'
  },
  
  // Production environment (GitHub Pages)
  production: {
    API_BASE: 'https://your-backend-domain.com', // You'll need to update this
    GOOGLE_CLIENT_ID: '10217611478-r6n5miarsidp97u82kt9fcqcle9ftcb3.apps.googleusercontent.com',
    FACEBOOK_APP_ID: 'YOUR_FACEBOOK_APP_ID'
  }
};

// Detect environment
const isProduction = window.location.hostname !== 'localhost' && 
                    window.location.hostname !== '127.0.0.1';

// Export current configuration
const currentConfig = isProduction ? CONFIG.production : CONFIG.development;

// Make config available globally
window.APP_CONFIG = currentConfig; 