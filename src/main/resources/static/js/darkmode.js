
(function () {
    if (localStorage.getItem('darkMode') === 'enabled') {
        document.body.classList.add('dark-mode');
    }
})();

document.addEventListener('DOMContentLoaded', function () {
    var isDark = localStorage.getItem('darkMode') === 'enabled';
    updateIcon(isDark);

    var btn = document.getElementById('darkModeToggle');
    if (btn) {
        btn.addEventListener('click', function () {
            isDark = document.body.classList.toggle('dark-mode');
            localStorage.setItem('darkMode', isDark ? 'enabled' : 'disabled');
            updateIcon(isDark);
        });
    }
});

function updateIcon(isDark) {
    var icon = document.getElementById('darkModeIcon');
    if (!icon) return;
    if (isDark) {
        icon.classList.remove('fa-moon');
        icon.classList.add('fa-sun');
    } else {
        icon.classList.remove('fa-sun');
        icon.classList.add('fa-moon');
    }
}
