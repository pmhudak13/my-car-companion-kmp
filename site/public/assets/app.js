document.addEventListener('DOMContentLoaded', function () {
  var hamburger = document.querySelector('.nav-hamburger');
  var nav = document.querySelector('.nav');
  if (hamburger && nav) {
    hamburger.addEventListener('click', function () {
      nav.classList.toggle('open');
      var expanded = nav.classList.contains('open');
      hamburger.setAttribute('aria-expanded', expanded);
    });
    // Close nav when a link is clicked
    nav.addEventListener('click', function (e) {
      if (e.target.tagName === 'A') nav.classList.remove('open');
    });
  }
});

// Analytics consent banner. GA4 starts in Consent Mode "denied" (set inline in each page's <head>);
// Accept grants analytics_storage. The choice is remembered in localStorage.
(function () {
  var KEY = 'mcc-analytics-consent';
  var stored = null;
  try { stored = localStorage.getItem(KEY); } catch (e) {}
  if (stored === 'granted' || stored === 'denied') return;

  function save(value) {
    try { localStorage.setItem(KEY, value); } catch (e) {}
    if (value === 'granted' && typeof window.gtag === 'function') {
      window.gtag('consent', 'update', { analytics_storage: 'granted' });
    }
  }

  function show() {
    var banner = document.createElement('div');
    banner.className = 'consent-banner';
    banner.setAttribute('role', 'region');
    banner.setAttribute('aria-label', 'Cookie choice');
    banner.innerHTML =
      '<p>We use Google Analytics cookies to see how the site is used. ' +
      'Accept to allow them, or Decline to browse without them. ' +
      '<a href="/privacy.html#information-we-collect">Privacy Policy</a></p>' +
      '<div class="consent-actions">' +
      '<button type="button" class="btn btn-primary" data-consent="granted">Accept</button>' +
      '<button type="button" class="btn btn-secondary" data-consent="denied">Decline</button>' +
      '</div>';
    banner.addEventListener('click', function (e) {
      var choice = e.target.getAttribute && e.target.getAttribute('data-consent');
      if (!choice) return;
      save(choice);
      banner.remove();
    });
    // Right after the skip link, so keyboard users reach it early without it taking the first tab stop.
    var skip = document.querySelector('.skip-link');
    document.body.insertBefore(banner, skip ? skip.nextSibling : document.body.firstChild);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', show);
  else show();
})();
