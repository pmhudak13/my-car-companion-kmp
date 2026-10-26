  // ── Fatal error handler (catches JS errors and Supabase CDN failures) ──
  function showInitError(msg) {
    var loader = document.getElementById('init-loader');
    if (loader) {
      loader.innerHTML =
        '<div style="text-align:center;padding:32px;max-width:420px">'
        + '<p style="color:#f08080;font-size:17px;margin-bottom:10px">Something went wrong</p>'
        + '<p style="color:#8fa3bf;font-size:13px;margin-bottom:20px">' + (msg || 'Unknown error') + '</p>'
        + '<button onclick="location.reload()" style="padding:10px 24px;background:#6bbcde;color:#0f1c38;'
        + 'border:none;border-radius:8px;cursor:pointer;font-weight:700">Reload</button>'
        + '</div>';
    }
  }
  // Only intercept errors that occur while the init-loader is still blocking.
  // Post-init errors (e.g. from button handlers) are handled inline by each function.
  window.onerror = function(msg, src, line, col, err) {
    var loader = document.getElementById('init-loader');
    if (loader && loader.style.display !== 'none') {
      showInitError(err ? err.toString() : msg);
    }
  };

  // ── Supabase init ────────────────────────────────────────────
  const SUPABASE_URL  = 'https://eoqycogokelfmgwqydkj.supabase.co';
  const SUPABASE_ANON = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVvcXljb2dva2VsZm1nd3F5ZGtqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQwMzMzMjcsImV4cCI6MjA4OTYwOTMyN30.F1wDENidEb7impi5ZP-lBQW6OueZiv9-yv1TcqZor4g';

  const { createClient } = supabase;
  const sb = createClient(SUPABASE_URL, SUPABASE_ANON);

  // Safety net: if onAuthStateChange never fires (e.g. network issue before
  // Supabase can reach its servers), stop the infinite spinner after 12s.
  var _initTimer = setTimeout(function() {
    var loader = document.getElementById('init-loader');
    if (loader && loader.style.display !== 'none') {
      showInitError('Connection timed out. Please check your network and reload.');
    }
  }, 12000);

  // ── Detect OAuth errors in redirect URL ──────────────────────
  // Errors from Google come back as query params; errors from Supabase's
  // PKCE layer can come back in the hash. Check both.
  const _up = new URLSearchParams(window.location.search);
  const _hashParams = new URLSearchParams(window.location.hash.replace(/^#/, ''));
  const _oauthError = _up.get('error_description') || _up.get('error')
                   || _hashParams.get('error_description') || _hashParams.get('error');
  // Detect if we returned from an OAuth redirect — if the exchange then fails
  // we can show an actionable error instead of silently showing the auth form.
  const _hasOAuthCode = _up.has('code');

  // ── Auth state — single source of truth ──────────────────────
  // INITIAL_SESSION fires once on load (after any code exchange), so we
  // hide the loader here instead of in a separate getSession() call.
  // _recoveryPending: set to true when PASSWORD_RECOVERY fires so that
  // a deferred showSignedIn() (from INITIAL_SESSION) is suppressed.
  var _recoveryPending = false;

  sb.auth.onAuthStateChange((event, session) => {
    clearTimeout(_initTimer);
    document.getElementById('init-loader').style.display = 'none';
    if (event === 'INITIAL_SESSION') {
      if (session) {
        // Defer by one microtask so PASSWORD_RECOVERY (which fires async
        // right after for recovery links) can set _recoveryPending first.
        Promise.resolve().then(() => {
          if (!_recoveryPending) showSignedIn(session.user);
        });
      } else if (_oauthError) {
        showAlert('signin-alert', 'error', decodeURIComponent(_oauthError.replace(/\+/g, ' ')));
      } else if (_hasOAuthCode) {
        // A ?code= was present but the PKCE exchange produced no session.
        // Most common cause: the link was opened in a different browser than where
        // sign-up/sign-in was started (PKCE verifier lives in that browser's localStorage).
        showAlert('signin-alert', 'error',
          'This link must be opened in the same browser where you started signing in. ' +
          'Try signing in with your email and password instead, or request a new link.'
        );
      }
    } else if (event === 'SIGNED_IN') {
      showSignedIn(session.user);
    } else if (event === 'SIGNED_OUT') {
      document.getElementById('signed-in-view').style.display    = 'none';
      document.getElementById('new-password-view').style.display = 'none';
      document.getElementById('auth-view').style.display         = 'block';
      clearAlerts();
    } else if (event === 'PASSWORD_RECOVERY') {
      _recoveryPending = true;
      showNewPasswordView();
    } else if (event === 'USER_UPDATED' && session) {
      showSignedIn(session.user);
    }
  });

  // ── Tab switching ────────────────────────────────────────────
  function switchTab(tab) {
    document.getElementById('panel-signin').classList.toggle('active', tab === 'signin');
    document.getElementById('panel-signup').classList.toggle('active', tab === 'signup');
    document.getElementById('tab-signin').classList.toggle('active', tab === 'signin');
    document.getElementById('tab-signup').classList.toggle('active', tab === 'signup');
    document.getElementById('auth-sub').textContent =
      tab === 'signin' ? 'Sign in to your account' : 'Create your free account';
    clearAlerts();
  }

  // ── Sign In ──────────────────────────────────────────────────
  async function doSignIn() {
    const email    = document.getElementById('signin-email').value.trim();
    const password = document.getElementById('signin-password').value;
    if (!email || !password) { showAlert('signin-alert', 'error', 'Please enter your email and password.'); return; }

    setLoading('signin-btn', true);
    try {
      const { error } = await sb.auth.signInWithPassword({ email, password });
      if (error) showAlert('signin-alert', 'error', error.message);
    } catch (e) {
      showAlert('signin-alert', 'error', 'Network error — please check your connection and try again.');
    } finally {
      setLoading('signin-btn', false);
    }
  }

  // ── Sign Up ──────────────────────────────────────────────────
  async function doSignUp() {
    const email    = document.getElementById('signup-email').value.trim();
    const password = document.getElementById('signup-password').value;
    const confirm  = document.getElementById('signup-confirm').value;
    if (!email || !password) { showAlert('signup-alert', 'error', 'Please fill in all fields.'); return; }
    if (password.length < 8)  { showAlert('signup-alert', 'error', 'Password must be at least 8 characters.'); return; }
    if (password !== confirm)  { showAlert('signup-alert', 'error', 'Passwords do not match.'); return; }

    setLoading('signup-btn', true);
    try {
      const { error } = await sb.auth.signUp({
        email,
        password,
        // Hardcoded canonical URL — must be in Supabase → Auth → URL Configuration → Redirect URLs.
        options: { emailRedirectTo: 'https://mycarcompanion.org/app/' }
      });
      if (error) {
        showAlert('signup-alert', 'error', error.message);
      } else {
        showAlert('signup-alert', 'success', 'Account created! Check your email to confirm your address, then sign in.');
      }
    } catch (e) {
      showAlert('signup-alert', 'error', 'Network error — please check your connection and try again.');
    } finally {
      setLoading('signup-btn', false);
    }
  }

  // ── Google OAuth ─────────────────────────────────────────────
  async function signInWithGoogle() {
    await sb.auth.signInWithOAuth({
      provider: 'google',
      // Hardcoded so the redirect URL is always canonical regardless of www/non-www.
      // This URL must be in Supabase → Authentication → URL Configuration → Redirect URLs.
      options: { redirectTo: 'https://mycarcompanion.org/app/callback/' }
    });
  }

  // ── Password reset ───────────────────────────────────────────
  // Rate-limit: max 3 attempts per 10-minute window, then 60s cooldown per send.
  const RESET_WINDOW_MS  = 10 * 60 * 1000; // 10 minutes
  const RESET_MAX_TRIES  = 3;
  const RESET_COOLDOWN_MS = 60 * 1000;      // 60s between sends

  function _resetState() {
    try {
      const raw = localStorage.getItem('_pw_reset');
      return raw ? JSON.parse(raw) : { count: 0, windowStart: 0, lastSent: 0 };
    } catch { return { count: 0, windowStart: 0, lastSent: 0 }; }
  }
  function _saveResetState(s) {
    try { localStorage.setItem('_pw_reset', JSON.stringify(s)); } catch {}
  }

  async function doReset() {
    const email = document.getElementById('reset-email').value.trim();
    if (!email) { showAlert('reset-alert', 'error', 'Please enter your email address.'); return; }

    const now   = Date.now();
    const state = _resetState();

    // Reset window if expired
    if (now - state.windowStart > RESET_WINDOW_MS) {
      state.count = 0;
      state.windowStart = now;
    }

    // Per-send cooldown
    const cooldownLeft = Math.ceil((RESET_COOLDOWN_MS - (now - state.lastSent)) / 1000);
    if (state.lastSent && cooldownLeft > 0) {
      showAlert('reset-alert', 'error', `Please wait ${cooldownLeft}s before requesting another reset link.`);
      return;
    }

    // Max attempts in window
    if (state.count >= RESET_MAX_TRIES) {
      const windowLeft = Math.ceil((RESET_WINDOW_MS - (now - state.windowStart)) / 60000);
      showAlert('reset-alert', 'error', `Too many reset attempts. Try again in about ${windowLeft} minute${windowLeft !== 1 ? 's' : ''}.`);
      return;
    }

    setLoading('reset-btn', true);
    try {
      const { error } = await sb.auth.resetPasswordForEmail(email, {
        // Hardcoded canonical URL — must be in Supabase → Auth → URL Configuration → Redirect URLs.
        redirectTo: 'https://mycarcompanion.org/app/'
      });
      if (error) {
        showAlert('reset-alert', 'error', error.message);
      } else {
        state.count++;
        state.lastSent = Date.now();
        _saveResetState(state);
        showAlert('reset-alert', 'success', 'Reset link sent! Check your inbox.');
      }
    } catch (e) {
      showAlert('reset-alert', 'error', 'Network error — please check your connection and try again.');
    } finally {
      setLoading('reset-btn', false);
    }
  }

  // ── Sign Out ─────────────────────────────────────────────────
  async function doSignOut() {
    await sb.auth.signOut();
    // DOM cleanup is handled by the SIGNED_OUT branch in onAuthStateChange
  }

  // ── View helpers ─────────────────────────────────────────────
  function showSignedIn(user) {
    // If this sign-in originated from the Compose app (/webapp/), hand the session
    // back and auto-redirect so the user returns to where they started.
    if (new URLSearchParams(window.location.search).get('webapp') === 'true') {
      sb.auth.getSession().then(function(res) {
        var session = res.data && res.data.session;
        if (session && session.access_token) {
          try {
            sessionStorage.setItem('_mcc_handoff', JSON.stringify({
              access_token:  session.access_token,
              refresh_token: session.refresh_token || '',
              token_type:    'bearer',
              expires_in:    session.expires_in || 3600,
            }));
          } catch (_) {}
        }
        window.location.replace('/webapp/');
      });
      return;
    }

    document.getElementById('auth-view').style.display          = 'none';
    document.getElementById('reset-view').style.display         = 'none';
    document.getElementById('new-password-view').style.display  = 'none';
    document.getElementById('signed-in-view').style.display     = 'block';
    document.getElementById('user-email-display').textContent   = user.email || '';
    if (new URLSearchParams(window.location.search).get('checkout') === 'success') {
      document.getElementById('checkout-success-banner').style.display = 'block';
      history.replaceState(null, '', window.location.pathname);
    }
  }

  function showReset() {
    document.getElementById('auth-view').style.display  = 'none';
    document.getElementById('reset-view').style.display = 'block';
  }

  function showAuth() {
    document.getElementById('reset-view').style.display         = 'none';
    document.getElementById('new-password-view').style.display  = 'none';
    document.getElementById('auth-view').style.display          = 'block';
  }

  function showNewPasswordView() {
    document.getElementById('auth-view').style.display          = 'none';
    document.getElementById('reset-view').style.display         = 'none';
    document.getElementById('signed-in-view').style.display     = 'none';
    document.getElementById('new-password-view').style.display  = 'block';
  }

  // ── Set new password (PASSWORD_RECOVERY flow) ────────────────
  async function doSetNewPassword() {
    const pw      = document.getElementById('newpw-password').value;
    const confirm = document.getElementById('newpw-confirm').value;
    if (!pw)           { showAlert('newpw-alert', 'error', 'Please enter a new password.'); return; }
    if (pw.length < 8) { showAlert('newpw-alert', 'error', 'Password must be at least 8 characters.'); return; }
    if (pw !== confirm) { showAlert('newpw-alert', 'error', 'Passwords do not match.'); return; }

    setLoading('newpw-btn', true);
    try {
      const { error } = await sb.auth.updateUser({ password: pw });
      if (error) {
        showAlert('newpw-alert', 'error', error.message);
      } else {
        showAlert('newpw-alert', 'success', 'Password updated! Taking you to your account…');
        setTimeout(async () => {
          const { data: { session } } = await sb.auth.getSession();
          if (session) showSignedIn(session.user);
        }, 1500);
      }
    } catch (e) {
      showAlert('newpw-alert', 'error', 'Network error — please check your connection and try again.');
    } finally {
      setLoading('newpw-btn', false);
    }
  }

  // ── Open web app — passes live session via sessionStorage ────
  // sessionStorage is tab-scoped and not visible in browser history,
  // server logs, or referrer headers. The webapp reads and clears it immediately.
  async function openWebApp(e) {
    e.preventDefault();
    try {
      const { data: { session } } = await sb.auth.getSession();
      if (session && session.access_token) {
        try {
          sessionStorage.setItem('_mcc_handoff', JSON.stringify({
            access_token:  session.access_token,
            refresh_token: session.refresh_token || '',
            token_type:    'bearer',
            expires_in:    session.expires_in || 3600,
          }));
        } catch (_) { /* private browsing — sessionStorage blocked, webapp will use its own session */ }
        window.location.href = '/webapp/';
      } else {
        window.location.href = '/webapp/';
      }
    } catch (_) {
      window.location.href = '/webapp/';
    }
  }

  // ── Utility ──────────────────────────────────────────────────
  function showAlert(id, type, msg) {
    const el = document.getElementById(id);
    el.className = 'alert ' + type + ' show';
    el.textContent = msg;
  }

  function clearAlerts() {
    document.querySelectorAll('.alert').forEach(el => el.className = 'alert');
  }

  function setLoading(btnId, loading) {
    const btn = document.getElementById(btnId);
    if (!btn) return;
    btn.disabled = loading;
    if (loading) {
      btn._orig = btn.textContent;
      btn.textContent = 'Please wait…';
    } else {
      btn.textContent = btn._orig || btn.textContent;
    }
  }
