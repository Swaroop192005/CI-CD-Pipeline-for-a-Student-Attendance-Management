#!/usr/bin/env bash
# Drives the portal on a provisioned node with a real browser and saves
# screenshots. Exists because every curl health check passed while the login
# flow was broken -- see docs/stage-13-configuration-management.md section 7.7.
set -euo pipefail
BASE="${1:?usage: browse-node.sh <base-url> <output-prefix>}"
OUT="${2:?usage: browse-node.sh <base-url> <output-prefix>}"
python3 - "$BASE" "$OUT" <<'PY'
import sys, time, tempfile
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By

base, out = sys.argv[1], sys.argv[2]
o = Options()
o.binary_location = "/opt/pw-browsers/chromium"
for a in ("--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
          "--window-size=1500,1100", "--disable-gpu", "--no-proxy-server",
          "--remote-allow-origins=*", "--hide-scrollbars"):
    o.add_argument(a)
o.add_argument("--user-data-dir=" + tempfile.mkdtemp(prefix="cr-"))
d = webdriver.Chrome(service=Service(executable_path="/usr/local/bin/chromedriver"), options=o)
d.set_page_load_timeout(60)

def visit(path, name, wait=2.0):
    d.get(base + path)
    time.sleep(wait)
    d.save_screenshot(f"{out}-{name}.png")
    rows = len(d.find_elements(By.CSS_SELECTOR, "tbody tr"))
    print(f"  {path:<12} -> {d.current_url:<50} rows={rows:<4} {d.title}")

try:
    visit("/login", "01-login", 1.5)
    d.find_element(By.ID, "username").send_keys("admin1")
    d.find_element(By.ID, "password").send_keys("Admin@123")
    d.find_element(By.CSS_SELECTOR, "button[type=submit]").click()
    time.sleep(2.5)
    d.save_screenshot(f"{out}-02-dashboard.png")
    print(f"  login submit -> {d.current_url}")
    assert "/dashboard" in d.current_url, f"login did not reach the dashboard: {d.current_url}"
    visit("/attendance", "03-records")
    visit("/students",   "04-students")
    visit("/review",     "05-review")
finally:
    d.quit()
PY
