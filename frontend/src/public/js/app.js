(function(){
  // Backend API base URL - this is where your Java server is running
  const API_BASE = 'http://localhost:8080';
  
  const state = { token: null, page: 0, pageSize: 10 };
  const $ = (s) => document.querySelector(s);
  const api = async (path, opts={}) => {
    const headers = { 'Content-Type': 'application/json' };
    const options = { method: opts.method || 'GET', headers };
    if (opts.body) options.body = JSON.stringify(opts.body);
    // Add the API_BASE to the path to point to your backend
    const fullUrl = path.startsWith('http') ? path : `${API_BASE}${path}`;
    const res = await fetch(fullUrl, options);
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  };

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
    console.log('doLogin() called'); // Debug: function entry
    // Demo only: no backend auth. Store a fake token and enable app.
    const email = $('#login_email').value.trim();
    const pwd = $('#login_password').value.trim();
    if (!email || !pwd){ $('#loginMsg').textContent = 'Please enter email and password'; return; }
    state.token = 'demo-token';
    $('#loginMsg').textContent = 'Logged in!';
    enableApp(true);
    setView('users');
    console.log('About to call refreshUsers()'); // Debug: before API call
    await refreshUsers();
    console.log('refreshUsers() completed'); // Debug: after API call
  }

  function logout(){
    state.token = null;
    enableApp(false);
    setView('login');
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
        list.forEach(u => {
          const tr = document.createElement('tr');
          tr.innerHTML = `
            <td>${u.id ?? ''}</td>
            <td>${u.username ?? ''}</td>
            <td>${u.email ?? ''}</td>
            <td>${u.role ?? ''}</td>
            <td>${u.status ?? ''}</td>
            <td><button class="btn secondary" data-user-id="${u.id}">View</button></td>
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

  // Events
  document.addEventListener('click', (e)=>{
    if (e.target.matches('.nav-btn')){
      const view = e.target.getAttribute('data-view');
      if (view === 'users' && !state.token){ return; }
      setView(view);
    }
    if (e.target.id === 'btnLogin') doLogin();
    if (e.target.id === 'btnRefresh') refreshUsers();
    if (e.target.id === 'btnLogout') logout();
    if (e.target.id === 'btnSearch') refreshUsers();
    if (e.target.id === 'btnTestAPI') testAPI();
    if (e.target.matches('button[data-user-id]')){
      const id = e.target.getAttribute('data-user-id');
      viewUser(id);
    }
  });

  // Init
  enableApp(false);
  setView('login');
})(); 