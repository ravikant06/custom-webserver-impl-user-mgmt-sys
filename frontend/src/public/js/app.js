(function(){
  // Backend API base URL - this is where your Java server is running
  const API_BASE = 'http://localhost:8080';
  
  const state = { 
    token: localStorage.getItem('auth_token'), 
    user: JSON.parse(localStorage.getItem('user_info') || 'null'),
    page: 0, 
    pageSize: 10 
  };
  
  const $ = (s) => document.querySelector(s);
  
  const api = async (path, opts={}) => {
    const headers = { 'Content-Type': 'application/json' };
    
    // Add authorization header if token exists
    if (state.token && !opts.skipAuth) {
      headers['Authorization'] = `Bearer ${state.token}`;
    }
    
    const options = { method: opts.method || 'GET', headers };
    if (opts.body) options.body = JSON.stringify(opts.body);
    
    // Add the API_BASE to the path to point to your backend
    const fullUrl = path.startsWith('http') ? path : `${API_BASE}${path}`;
    
    try {
      const res = await fetch(fullUrl, options);
      
      // Handle 401 Unauthorized - token expired or invalid
      if (res.status === 401) {
        console.warn('Authentication failed, clearing token');
        clearAuthState();
        setView('login');
        enableApp(false);
        throw new Error('Authentication required');
      }
      
      if (!res.ok) {
        const errorText = await res.text();
        throw new Error(errorText || `HTTP ${res.status}`);
      }
      
      return res.json();
    } catch (error) {
      console.error('API call failed:', error);
      throw error;
    }
  };
  
  // Authentication helper functions
  function saveAuthState(token, userInfo) {
    state.token = token;
    state.user = userInfo;
    localStorage.setItem('auth_token', token);
    localStorage.setItem('user_info', JSON.stringify(userInfo));
  }
  
  function clearAuthState() {
    state.token = null;
    state.user = null;
    localStorage.removeItem('auth_token');
    localStorage.removeItem('user_info');
  }
  
  function isAuthenticated() {
    return state.token && state.user;
  }

  // Views handling
  function setView(name){
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
    $(`#view-${name}`).classList.add('active');
    document.querySelector(`.nav-btn[data-view="${name}"]`)?.classList.add('active');
  }

  function enableApp(enabled){
    document.querySelectorAll('.nav-btn[data-view="users"]').forEach(btn => btn.disabled = !enabled);
  }

  // Login
  async function doLogin(){
    console.log('doLogin() called');
    const email = $('#login_email').value.trim();
    const pwd = $('#login_password').value.trim();
    const msgEl = $('#loginMsg');
    
    if (!email || !pwd) { 
      msgEl.textContent = 'Please enter email and password'; 
      msgEl.style.color = 'var(--danger)';
      return; 
    }
    
    try {
      msgEl.textContent = 'Logging in...';
      msgEl.style.color = 'var(--muted)';
      
      // Call the real authentication API
      const response = await api('/api/auth/login', {
        method: 'POST',
        body: { email, password: pwd },
        skipAuth: true // Don't send auth header for login
      });
      
      if (response.success && response.data) {
        const loginData = response.data;
        
        // Save authentication state
        saveAuthState(loginData.token, {
          userId: loginData.userId,
          username: loginData.username,
          email: loginData.email,
          firstName: loginData.firstName,
          lastName: loginData.lastName,
          role: loginData.role,
          status: loginData.status
        });
        
        msgEl.textContent = 'Login successful!';
        msgEl.style.color = 'var(--ok)';
        
        // Update UI
        updateUserDisplay();
        enableApp(true);
        setView('users');
        
        // Load users
        await refreshUsers();
        
        console.log('Login successful for user:', loginData.email);
      } else {
        msgEl.textContent = 'Login failed: Invalid response format';
        msgEl.style.color = 'var(--danger)';
      }
      
    } catch (error) {
      console.error('Login failed:', error);
      msgEl.textContent = 'Login failed: ' + error.message;
      msgEl.style.color = 'var(--danger)';
    }
  }

  async function logout(){
    try {
      // Call logout endpoint (mainly for logging purposes)
      if (state.token) {
        await api('/api/auth/logout', { method: 'POST' });
      }
    } catch (error) {
      console.warn('Logout API call failed:', error);
    } finally {
      // Clear auth state regardless of API call result
      clearAuthState();
      enableApp(false);
      setView('login');
      $('#loginMsg').textContent = '';
      // Clear role-based styling
      document.body.className = '';
      console.log('User logged out');
    }
  }
  
  function updateUserDisplay() {
    if (state.user) {
      // Update any user-specific UI elements
      const userDisplayElements = document.querySelectorAll('.user-display');
      userDisplayElements.forEach(el => {
        el.textContent = `${state.user.firstName} ${state.user.lastName} (${state.user.role})`;
      });
      
      // Apply role-based styling
      const roleClass = `user-role-${state.user.role.toLowerCase()}`;
      document.body.className = roleClass;
      
      // Debug logging
      console.log('Updated user display:', {
        user: state.user,
        roleClass: roleClass,
        bodyClass: document.body.className,
        isAdmin: state.user.role === 'ADMIN'
      });
      
      // Force show/hide admin elements based on role
      const adminElements = document.querySelectorAll('.admin-only');
      adminElements.forEach(el => {
        if (state.user.role === 'ADMIN') {
          el.style.display = 'inline-block';
        } else {
          el.style.display = 'none';
        }
      });
    }
  }

  // Users
  async function refreshUsers(){
    console.log('refreshUsers() called'); // Debug: function entry
    try{
      const search = $('#search_input').value.trim();
      const status = $('#status_filter').value.trim();
      let url = `/api/users?page=${state.page}&pageSize=${state.pageSize}`;
      if (search) url += `&search=${encodeURIComponent(search)}`;
      if (status) url += `&status=${encodeURIComponent(status)}`;
      console.log('Making API call to:', url); // Debug: URL being called
      const data = await api(url);
      console.log('API Response:', data); // Debug log to see the response structure
      
      // Handle different response structures
      let list = [];
      if (Array.isArray(data?.data)) {
        // New API format: {data: [...], page: 0, totalElements: 5}
        list = data.data;
      } else if (Array.isArray(data?.data?.content)) {
        // Pagination format: {data: {content: [...], page: 0}}
        list = data.data.content;
      } else if (Array.isArray(data)) {
        // Simple array format: [...]
        list = data;
      }
      const tbody = $('#users_table');
      console.log('Found tbody element:', tbody); // Debug: DOM element
      console.log('Users list to render:', list); // Debug: final list
      tbody.innerHTML = '';
      
      if (list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--muted)">No users found</td></tr>';
      } else {
        console.log(`Rendering ${list.length} users`); // Debug log
        const isAdmin = state.user && state.user.role === 'ADMIN';
        
        // Update user count in title
        const titleElement = document.querySelector('.title');
        if (titleElement) {
          const userInfo = titleElement.querySelector('.user-info');
          const baseTitle = titleElement.childNodes[0].textContent.replace(/\(\d+\)/, '').trim();
          titleElement.childNodes[0].textContent = `${baseTitle} (${list.length})`;
        }
        
        list.forEach(u => {
          const tr = document.createElement('tr');
          
          // Role badge styling
          const roleBadge = u.role === 'ADMIN' 
            ? '<span style="background: var(--danger); color: white; padding: 2px 6px; border-radius: 4px; font-size: 11px;">ADMIN</span>'
            : '<span style="background: var(--accent); color: white; padding: 2px 6px; border-radius: 4px; font-size: 11px;">USER</span>';
          
          // Status badge styling
          let statusBadge = '';
          switch(u.status) {
            case 'ACTIVE':
              statusBadge = '<span style="background: var(--ok); color: white; padding: 2px 6px; border-radius: 4px; font-size: 11px;">ACTIVE</span>';
              break;
            case 'INACTIVE':
              statusBadge = '<span style="background: var(--muted); color: white; padding: 2px 6px; border-radius: 4px; font-size: 11px;">INACTIVE</span>';
              break;
            case 'SUSPENDED':
              statusBadge = '<span style="background: var(--danger); color: white; padding: 2px 6px; border-radius: 4px; font-size: 11px;">SUSPENDED</span>';
              break;
            default:
              statusBadge = u.status ?? '';
          }
          
          // Action buttons (admin gets edit/delete, all users get view)
          let actionButtons = `<button class="btn secondary" data-user-id="${u.id}" data-action="view">View</button>`;
          if (isAdmin) {
            actionButtons += ` <button class="btn" data-user-id="${u.id}" data-action="edit" style="font-size: 11px; padding: 6px 8px;">Edit</button>`;
            actionButtons += ` <button class="btn danger" data-user-id="${u.id}" data-action="delete" style="font-size: 11px; padding: 6px 8px;">Delete</button>`;
          }
          
          tr.innerHTML = `
            <td>${u.id ?? ''}</td>
            <td>${u.username ?? ''}</td>
            <td>${u.email ?? ''}</td>
            <td>${roleBadge}</td>
            <td>${statusBadge}</td>
            <td style="white-space: nowrap;">${actionButtons}</td>
          `;
          tbody.appendChild(tr);
        });
      }
    }catch(e){
      console.error('Error refreshing users:', e);
      const tbody = $('#users_table');
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--danger)">Error loading users: ' + e.message + '</td></tr>';
    }
  }

  // Test function to debug API calls
  async function testAPI(){
    console.log('=== TESTING API ===');
    try {
      console.log('1. Testing basic fetch to backend...');
      const response = await fetch('http://localhost:8080/health');
      console.log('Health check response:', response.status, response.statusText);
      
      console.log('2. Testing API wrapper function...');
      const healthData = await api('/health');
      console.log('Health data:', healthData);
      
      console.log('3. Testing users API...');
      const usersData = await api('/api/users');
      console.log('Users data structure:', usersData);
      console.log('Users data type:', typeof usersData);
      console.log('Users data.data type:', typeof usersData?.data);
      console.log('Is users data.data array?', Array.isArray(usersData?.data));
      
      console.log('4. Testing DOM selection...');
      const tbody = document.querySelector('#users_table');
      console.log('Table body element:', tbody);
      console.log('Table body parent:', tbody?.parentElement);
      
      if (tbody) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:green;">API TEST SUCCESS - Check console for details</td></tr>';
      }
      
    } catch (error) {
      console.error('API Test failed:', error);
      const tbody = document.querySelector('#users_table');
      if (tbody) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:red;">API TEST FAILED - Check console for details</td></tr>';
      }
    }
  }

  async function viewUser(id){
    if (!id) return;
    try{
      const data = await api(`/api/users/${id}`);
      const user = data?.data || data;
      const panel = $('#user_details');
      panel.classList.remove('hidden');
      panel.innerHTML = `
        <h3>User #${user.id ?? ''}</h3>
        <div class="grid two">
          <div><strong>Username:</strong> ${user.username ?? ''}</div>
          <div><strong>Email:</strong> ${user.email ?? ''}</div>
          <div><strong>First Name:</strong> ${user.firstName ?? ''}</div>
          <div><strong>Last Name:</strong> ${user.lastName ?? ''}</div>
          <div><strong>Role:</strong> ${user.role ?? ''}</div>
          <div><strong>Status:</strong> ${user.status ?? ''}</div>
        </div>
      `;
    }catch(e){
      alert(e.message);
    }
  }

  // User Creation Functions
  function showAddUserModal() {
    $('#view-add-user').classList.add('active');
    // Clear form
    ['new_username', 'new_email', 'new_firstName', 'new_lastName', 'new_phone', 'new_password'].forEach(id => {
      $(`#${id}`).value = '';
    });
    $('#new_role').value = 'USER';
    $('#createUserMsg').textContent = '';
  }

  function hideAddUserModal() {
    $('#view-add-user').classList.remove('active');
  }

  async function createUser() {
    const msgEl = $('#createUserMsg');
    const createBtn = $('#btnCreateUser');
    
    try {
      // Get form values
      const userData = {
        username: $('#new_username').value.trim(),
        email: $('#new_email').value.trim(),
        firstName: $('#new_firstName').value.trim(),
        lastName: $('#new_lastName').value.trim(),
        phone: $('#new_phone').value.trim() || null,
        role: $('#new_role').value,
        password: $('#new_password').value
      };

      // Basic validation
      if (!userData.username || !userData.email || !userData.firstName || !userData.lastName || !userData.password) {
        msgEl.textContent = 'Please fill in all required fields';
        msgEl.style.color = 'var(--danger)';
        return;
      }

      // Additional validation
      if (userData.password.length < 6) {
        msgEl.textContent = 'Password must be at least 6 characters long';
        msgEl.style.color = 'var(--danger)';
        return;
      }

      // Disable button and show loading
      createBtn.disabled = true;
      createBtn.textContent = 'Creating...';
      msgEl.textContent = 'Creating user...';
      msgEl.style.color = 'var(--muted)';

      // Call API
      const response = await api('/api/users', {
        method: 'POST',
        body: userData
      });

      if (response.success) {
        msgEl.textContent = 'User created successfully!';
        msgEl.style.color = 'var(--ok)';
        
        // Refresh user list and close modal after delay
        setTimeout(() => {
          hideAddUserModal();
          refreshUsers();
        }, 1500);
      } else {
        msgEl.textContent = 'Failed to create user: ' + (response.message || 'Unknown error');
        msgEl.style.color = 'var(--danger)';
      }

    } catch (error) {
      console.error('Create user failed:', error);
      msgEl.textContent = 'Failed to create user: ' + error.message;
      msgEl.style.color = 'var(--danger)';
    } finally {
      // Re-enable button
      createBtn.disabled = false;
      createBtn.textContent = 'Create User';
    }
  }

  // User Edit Function (placeholder for now)
  async function editUser(id) {
    alert(`Edit user functionality coming soon! (User ID: ${id})`);
    // TODO: Implement edit user modal and functionality
  }

  // User Delete Function
  async function deleteUser(id) {
    if (!confirm('Are you sure you want to delete this user? This action cannot be undone.')) {
      return;
    }

    try {
      const response = await api(`/api/users/${id}`, {
        method: 'DELETE'
      });

      if (response.success) {
        alert('User deleted successfully!');
        await refreshUsers(); // Refresh the user list
      } else {
        alert('Failed to delete user: ' + (response.message || 'Unknown error'));
      }

    } catch (error) {
      console.error('Delete user failed:', error);
      alert('Failed to delete user: ' + error.message);
    }
  }

  // Events
  document.addEventListener('click', (e)=>{
    if (e.target.matches('.nav-btn')){
      const view = e.target.getAttribute('data-view');
      if (view === 'users' && !isAuthenticated()){ return; }
      setView(view);
    }
    // Close modal when clicking outside
    if (e.target.id === 'view-add-user') {
      hideAddUserModal();
    }
    if (e.target.id === 'btnLogin') doLogin();
    if (e.target.id === 'btnRefresh') refreshUsers();
    if (e.target.id === 'btnLogout') logout();
    if (e.target.id === 'btnSearch') refreshUsers();
    if (e.target.id === 'btnTestAPI') testAPI();
    if (e.target.id === 'btnAddUser') showAddUserModal();
    if (e.target.id === 'btnCreateUser') createUser();
    if (e.target.id === 'btnCancelCreate') hideAddUserModal();
    if (e.target.id === 'btnCloseModal') hideAddUserModal();
    if (e.target.matches('button[data-user-id]')){
      const id = e.target.getAttribute('data-user-id');
      const action = e.target.getAttribute('data-action') || 'view';
      
      switch(action) {
        case 'view':
          viewUser(id);
          break;
        case 'edit':
          editUser(id);
          break;
        case 'delete':
          deleteUser(id);
          break;
      }
    }
  });
  
  // Handle Enter key in login form
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      if (e.target.id === 'login_email' || e.target.id === 'login_password') {
        doLogin();
      }
    }
  });

  // Initialize app
  async function initializeApp() {
    // Check if user is already logged in
    if (isAuthenticated()) {
      try {
        // Validate existing token
        await api('/api/auth/validate');
        console.log('Existing token valid, user already logged in');
        updateUserDisplay();
        enableApp(true);
        setView('users');
        await refreshUsers();
      } catch (error) {
        console.warn('Existing token invalid, clearing auth state');
        clearAuthState();
        enableApp(false);
        setView('login');
      }
    } else {
      enableApp(false);
      setView('login');
    }
  }
  
  // Start the app
  initializeApp();
})(); 