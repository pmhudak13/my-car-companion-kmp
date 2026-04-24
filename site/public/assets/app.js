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
