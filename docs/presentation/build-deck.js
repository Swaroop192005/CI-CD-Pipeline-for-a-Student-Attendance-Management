const pptxgen = require("pptxgenjs");

// Content-informed palette: a pipeline's verdict is literally green or red,
// so the accents are the signal colours of a build, on a near-black base.
const THEME = {
  name: "Pipeline Signal",
  headFontFace: "Cambria",
  bodyFontFace: "Calibri",
  colors: {
    dk1: "16191D", lt1: "FFFFFF",
    dk2: "36454F", lt2: "F3F5F6",
    accent1: "00A86B",  // pass
    accent2: "0E8C9B",  // teal, supporting
    accent3: "D7263D",  // fail
    accent4: "E9A317",  // attention
    accent5: "7A8B9C",  // muted
    accent6: "21295C",
    hlink: "0E8C9B", folHlink: "7A8B9C",
  },
};

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE";            // 13.3 x 7.5, set before any slide
pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
pres.author = "Swaroop Naik";
pres.title = "CI/CD Pipeline for a Student Attendance Management Portal";
pres.subject = "DevOps project submission - Roll 23102A0001";

const C = pres.SchemeColor;
const W = 13.33, H = 7.5, M = 0.7;

// ---------------------------------------------------------------- layouts --
pres.defineSlideMaster({
  title: "TITLE_DARK", background: { color: THEME.colors.dk1 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: M, y: 2.3, w: W - 2*M, h: 1.5,
        fontSize: 40, bold: true, color: C.background1, align: "left" }, text: " " } },
    { placeholder: { options: { name: "body", type: "body", x: M, y: 3.8, w: W - 2*M, h: 0.9,
        fontSize: 18, color: THEME.colors.accent1, align: "left" }, text: " " } },
    { placeholder: { options: { name: "byline", type: "body", x: M, y: 5.6, w: W - 2*M, h: 1.1,
        fontSize: 14, color: THEME.colors.accent5, align: "left" }, text: " " } },
  ],
});

pres.defineSlideMaster({
  title: "SECTION", background: { color: THEME.colors.accent6 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: M, y: 3.0, w: W - 2*M, h: 1.1,
        fontSize: 34, bold: true, color: C.background1, align: "left" }, text: " " } },
    { placeholder: { options: { name: "body", type: "body", x: M, y: 4.1, w: W - 2*M, h: 0.8,
        fontSize: 16, color: THEME.colors.accent1, align: "left" }, text: " " } },
  ],
  slideNumber: { x: W - 0.9, y: H - 0.5, fontSize: 10, color: THEME.colors.accent5 },
});

// Title-only: the frame most content slides sit on, composed per slide.
pres.defineSlideMaster({
  title: "CONTENT", background: { color: C.background1 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: M, y: 0.45, w: W - 2*M, h: 0.85,
        fontSize: 32, bold: true, color: C.text1, align: "left" }, text: " " } },
    { text: { text: "Student Attendance Portal · CI/CD Pipeline · Swaroop Naik · 23102A0001",
        options: { x: M, y: H - 0.52, w: 9.5, h: 0.3, fontSize: 9,
                   color: THEME.colors.accent5, isTextBox: true, margin: 0 } } },
  ],
  slideNumber: { x: W - 0.9, y: H - 0.5, fontSize: 10, color: THEME.colors.accent5 },
});

pres.defineSlideMaster({
  title: "CONTENT_DARK", background: { color: THEME.colors.dk1 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: M, y: 0.45, w: W - 2*M, h: 0.85,
        fontSize: 32, bold: true, color: C.background1, align: "left" }, text: " " } },
    { text: { text: "Student Attendance Portal · CI/CD Pipeline · Swaroop Naik · 23102A0001",
        options: { x: M, y: H - 0.52, w: 9.5, h: 0.3, fontSize: 9,
                   color: THEME.colors.accent5, isTextBox: true, margin: 0 } } },
  ],
  slideNumber: { x: W - 0.9, y: H - 0.5, fontSize: 10, color: THEME.colors.accent5 },
});

// ------------------------------------------------------------- helpers -----
let card = 0;
function panel(s, x, y, w, h, fill) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x, y, w, h, rectRadius: 0.08, fill: { color: fill || THEME.colors.lt2 },
    line: { color: fill || THEME.colors.lt2, width: 0.5 },
    objectName: `panel-${++card}`,
  });
}
function chip(s, x, y, w, h, fill, label, labelColor, size) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x, y, w, h, rectRadius: 0.06, fill: { color: fill },
    line: { color: fill, width: 0.5 }, objectName: `chip-${++card}`,
  });
  s.addText(label, { x, y, w, h, fontSize: size || 10, bold: true, color: labelColor,
    align: "center", valign: "middle", isTextBox: true, margin: 0 });
}
function stat(s, x, y, w, value, label, color) {
  s.addText(value, { x, y, w, h: 0.9, fontSize: 48, bold: true, color,
    align: "left", isTextBox: true, margin: 0 });
  s.addText(label, { x, y: y + 0.88, w, h: 0.6, fontSize: 12, color: THEME.colors.accent5,
    align: "left", isTextBox: true, margin: 0 });
}

// =========================================================== 1. TITLE ======
pres.addSection({ title: "Opening" });
let s = pres.addSlide({ masterName: "TITLE_DARK", sectionTitle: "Opening" });
s.addText("CI/CD Pipeline for a Student\nAttendance Management Portal", { placeholder: "title" });
s.addText("Build · Test · Gate · Containerise · Deploy · Provision", { placeholder: "body" });
s.addText([
  { text: "Swaroop Naik", options: { bold: true, color: "FFFFFF", fontSize: 16, breakLine: true } },
  { text: "Roll 23102A0001", options: { breakLine: true } },
  { text: "Guide: Snehal Andhare", options: {} },
], { placeholder: "byline" });
// The motif: the eleven pipeline stages as a row of chips, reused later.
for (let i = 0; i < 11; i++) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: M + i * 0.52, y: 1.55, w: 0.42, h: 0.14, rectRadius: 0.05,
    fill: { color: i < 11 ? THEME.colors.accent1 : THEME.colors.accent5 },
    line: { color: THEME.colors.accent1, width: 0.5 }, objectName: `stage-chip-${i}`,
  });
}
s.addNotes("Fifteen-task DevOps project. The application is a vehicle; the deliverable is the pipeline that builds, verifies and ships it.");

// =========================================================== 2. PROBLEM ====
pres.addSection({ title: "Problem and solution" });
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "Problem and solution" });
s.addText("The problem", { placeholder: "title" });
panel(s, M, 1.5, 5.9, 4.6);
s.addText("Paper registers", { x: M + 0.35, y: 1.75, w: 5.2, h: 0.4, fontSize: 20, bold: true,
  color: THEME.colors.dk2, isTextBox: true, margin: 0 });
s.addText([
  { text: "Attendance written on paper, keyed in later", options: { bullet: true, breakLine: true } },
  { text: "A student learns their standing weeks afterwards", options: { bullet: true, breakLine: true } },
  { text: "Eligibility disputes have no audit trail", options: { bullet: true, breakLine: true } },
  { text: "Corrections are untraceable", options: { bullet: true } },
], { x: M + 0.35, y: 2.3, w: 5.2, h: 2.4, fontSize: 14, color: THEME.colors.dk2,
     paraSpaceAfter: 8, isTextBox: true, margin: 0 });
s.addText("9–14 days", { x: M + 0.35, y: 4.85, w: 5.2, h: 0.7, fontSize: 36, bold: true,
  color: THEME.colors.accent3, isTextBox: true, margin: 0 });
s.addText("measured delay from mark to student visibility", { x: M + 0.35, y: 5.5, w: 5.2, h: 0.4,
  fontSize: 11, color: THEME.colors.accent5, isTextBox: true, margin: 0 });

panel(s, 6.9, 1.5, 5.73, 4.6, THEME.colors.dk1);
s.addText("A system of record", { x: 7.25, y: 1.75, w: 5.1, h: 0.4, fontSize: 20, bold: true,
  color: "FFFFFF", isTextBox: true, margin: 0 });
s.addText([
  { text: "Faculty opens a session; students mark themselves", options: { bullet: true, breakLine: true } },
  { text: "Closing records everyone who did not as absent", options: { bullet: true, breakLine: true } },
  { text: "Every change attributed and timestamped", options: { bullet: true, breakLine: true } },
  { text: "Only extreme cases escalate to a conversation", options: { bullet: true } },
], { x: 7.25, y: 2.3, w: 5.1, h: 2.4, fontSize: 14, color: "E8ECEF",
     paraSpaceAfter: 8, isTextBox: true, margin: 0 });
s.addText("Immediate", { x: 7.25, y: 4.85, w: 5.1, h: 0.7, fontSize: 36, bold: true,
  color: THEME.colors.accent1, isTextBox: true, margin: 0 });
s.addText("the student sees the record as it is written", { x: 7.25, y: 5.5, w: 5.1, h: 0.4,
  fontSize: 11, color: THEME.colors.accent5, isTextBox: true, margin: 0 });
s.addNotes("The delay was the measured baseline in Stage 1. Removing the approval queue is what collapsed it to zero.");

// =========================================================== 3. STACK ======
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "Problem and solution" });
s.addText("The technology stack", { placeholder: "title" });
const stackRows = [
  ["Language & framework", "Java 21 · Spring Boot 3.5.16", "One artefact: runs standalone or deploys to Tomcat"],
  ["Web & persistence", "Thymeleaf · Spring Data JPA · H2", "Server-rendered, so there is one deployable to ship"],
  ["Security", "Spring Security 6.5 · BCrypt · WebAuthn", "Role rules, hashed passwords, passkey sign-in"],
  ["Build & test", "Maven · JUnit 5 · Selenium 4.50 · JaCoCo", "Unit, integration and browser levels in one reactor"],
  ["Pipeline", "Jenkins LTS · Configuration as Code", "The controller is rebuildable, not hand-clicked"],
  ["Runtime & infra", "Docker · registry:2 · Ansible · Puppet", "Package the app, provision the host"],
];
let ry = 1.45;
stackRows.forEach((r, i) => {
  panel(s, M, ry, W - 2*M, 0.78, i % 2 === 0 ? THEME.colors.lt2 : "FFFFFF");
  s.addText(r[0], { x: M + 0.25, y: ry + 0.06, w: 2.9, h: 0.66, fontSize: 12, bold: true,
    color: THEME.colors.accent2, valign: "middle", isTextBox: true, margin: 0 });
  s.addText(r[1], { x: M + 3.2, y: ry + 0.06, w: 4.3, h: 0.66, fontSize: 12, bold: true,
    color: C.text1, valign: "middle", isTextBox: true, margin: 0 });
  s.addText(r[2], { x: M + 7.6, y: ry + 0.06, w: 4.2, h: 0.66, fontSize: 11,
    color: THEME.colors.accent5, valign: "middle", isTextBox: true, margin: 0 });
  ry += 0.86;
});
s.addNotes("Every version here is the one the build actually resolves, not a version the report hopes for.");

// =========================================================== 4. PIPELINE ===
pres.addSection({ title: "The pipeline" });
s = pres.addSlide({ masterName: "SECTION", sectionTitle: "The pipeline" });
s.addText("The pipeline", { placeholder: "title" });
s.addText("Eleven stages from commit to a verified deployment", { placeholder: "body" });

s = pres.addSlide({ masterName: "CONTENT_DARK", sectionTitle: "The pipeline" });
s.addText("Eleven stages, one verdict", { placeholder: "title" });
const stages = [
  ["1", "Checkout"], ["2", "Build"], ["3", "Unit test"], ["4", "Package"],
  ["5", "Integration"], ["6", "Quality gate"], ["7", "Build image"],
  ["8", "Deploy container"], ["9", "Deploy Tomcat"], ["10", "Verify"], ["11", "Post"],
];
let sx = M;
stages.forEach(([n, label], i) => {
  const gate = i === 5;
  const w = 1.03;
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
    x: sx, y: 1.7, w, h: 1.5, rectRadius: 0.08,
    fill: { color: gate ? THEME.colors.accent4 : THEME.colors.dk2 },
    line: { color: gate ? THEME.colors.accent4 : "4A5A66", width: 1 },
    objectName: `pipeline-stage-${n}`,
  });
  s.addText(n, { x: sx, y: 1.82, w, h: 0.35, fontSize: 12, bold: true,
    color: gate ? "16191D" : THEME.colors.accent1, align: "center", isTextBox: true, margin: 0 });
  s.addText(label, { x: sx + 0.04, y: 2.2, w: w - 0.08, h: 0.85, fontSize: 10.5,
    color: gate ? "16191D" : "FFFFFF", align: "center", valign: "top", isTextBox: true, margin: 0 });
  sx += w + 0.09;
});
s.addText("▲", { x: M + 5*(1.03+0.09) + 0.3, y: 3.25, w: 0.4, h: 0.3, fontSize: 14,
  color: THEME.colors.accent4, align: "center", isTextBox: true, margin: 0 });
s.addText([
  { text: "The gate sits at stage 6, before the image is built.", options: { bold: true, color: "FFFFFF", breakLine: true } },
  { text: "If a browser journey fails, no image is published and nothing deploys. A check that runs after deployment can only report what users already have.", options: {} },
], { x: M, y: 3.6, w: 7.4, h: 1.3, fontSize: 14, color: "C9D1D9", isTextBox: true, margin: 0 });

panel(s, 8.5, 3.55, 4.13, 2.4, "1E2329");
s.addText("Tested against the same artefact", { x: 8.8, y: 3.75, w: 3.6, h: 0.4, fontSize: 13,
  bold: true, color: THEME.colors.accent1, isTextBox: true, margin: 0 });
s.addText("The gate starts a throwaway container from the WAR just packaged — the one that gets deployed if it passes. Testing a different build would make the gate decorative.",
  { x: 8.8, y: 4.2, w: 3.55, h: 1.6, fontSize: 11.5, color: "A9B4BE", isTextBox: true, margin: 0 });
s.addNotes("Stage ordering is the whole argument. Walk through: gate at 6, image at 7.");

// =========================================================== 5. TOOLS =====
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "The pipeline" });
s.addText("What each tool is for", { placeholder: "title" });
const tools = [
  ["Docker", THEME.colors.accent2, "Packages the app with its runtime, and provides the lab: eight containers standing in for machines."],
  ["Jenkins", THEME.colors.accent1, "Decides when a change ships. Eleven stages, defined in a Jenkinsfile that is versioned with the code."],
  ["Selenium", THEME.colors.accent4, "Drives a real browser through real tasks, so the gate tests the page a human sees."],
  ["Ansible", THEME.colors.accent6, "Declares the state a host should be in and converges it. Agentless, over SSH."],
  ["Puppet", THEME.colors.dk2, "The same job, masterless, with its own declarative language. Both are demonstrated."],
  ["Maven", THEME.colors.accent5, "One command compiles, tests and packages, so the pipeline needs no bespoke build steps."],
];
tools.forEach((t, i) => {
  const col = i % 3, row = Math.floor(i / 3);
  const x = M + col * 4.08, y = 1.5 + row * 2.45;
  panel(s, x, y, 3.85, 2.2);
  s.addShape(pres.shapes.OVAL, { x: x + 0.28, y: y + 0.28, w: 0.5, h: 0.5,
    fill: { color: t[1] }, line: { color: t[1], width: 1 }, objectName: `tool-dot-${i}` });
  s.addText(t[0], { x: x + 0.95, y: y + 0.3, w: 2.7, h: 0.46, fontSize: 18, bold: true,
    color: C.text1, valign: "middle", isTextBox: true, margin: 0 });
  s.addText(t[2], { x: x + 0.28, y: y + 0.95, w: 3.3, h: 1.1, fontSize: 11.5,
    color: THEME.colors.dk2, isTextBox: true, margin: 0 });
});
s.addNotes("If asked why both Ansible and Puppet: they are the two dominant tools; either alone would have sufficed functionally.");

// =========================================================== 6. TESTING ===
pres.addSection({ title: "Verification" });
s = pres.addSlide({ masterName: "SECTION", sectionTitle: "Verification" });
s.addText("Verification", { placeholder: "title" });
s.addText("129 tests at three levels, and what they caught", { placeholder: "body" });

s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "Verification" });
s.addText("Three levels, 129 tests", { placeholder: "title" });
stat(s, M, 1.45, 3.0, "129", "tests in the suite", THEME.colors.accent1);
stat(s, M + 3.2, 1.45, 3.0, "67.9%", "line coverage (JaCoCo)", THEME.colors.accent2);
stat(s, M + 6.4, 1.45, 3.0, "85.8%", "coverage of the service layer", THEME.colors.accent2);
stat(s, M + 9.6, 1.45, 2.3, "0", "failing", THEME.colors.accent1);

const levels = [
  ["19", "Selenium journeys", "A real browser completes real tasks across roles", THEME.colors.accent4, 3.6],
  ["7", "Integration tests", "Web layer and persistence together, through MockMvc", THEME.colors.accent2, 5.6],
  ["103", "Unit tests", "The business rules: percentages, thresholds, case tiers", THEME.colors.accent1, 7.6],
];
let ly = 3.3;
levels.forEach(([n, name, desc, colr, width], i) => {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: M, y: ly, w: width, h: 0.95, rectRadius: 0.08,
    fill: { color: colr }, line: { color: colr, width: 1 }, objectName: `level-${i}` });
  s.addText(n, { x: M + 0.25, y: ly, w: 1.0, h: 0.95, fontSize: 26, bold: true,
    color: "FFFFFF", valign: "middle", isTextBox: true, margin: 0 });
  s.addText(name, { x: M + 1.3, y: ly, w: width - 1.5, h: 0.95, fontSize: 14, bold: true,
    color: "FFFFFF", valign: "middle", isTextBox: true, margin: 0 });
  s.addText(desc, { x: M + width + 0.25, y: ly, w: 11.9 - width, h: 0.95, fontSize: 11.5,
    color: THEME.colors.accent5, valign: "middle", isTextBox: true, margin: 0 });
  ly += 1.12;
});
s.addNotes("The widths are deliberate: the pyramid is broad at the unit level and narrow at the browser level, because browser tests are slow and brittle.");

// =========================================================== 7. CAUGHT ====
s = pres.addSlide({ masterName: "CONTENT_DARK", sectionTitle: "Verification" });
s.addText("What the tests actually caught", { placeholder: "title" });
const bugs = [
  ["The roster rendered blank", "A template read a property that no longer existed. Thymeleaf treats a missing property as false, so the page silently hid its own Stop and Strike buttons.", "Passed every unit test. Only a browser saw it."],
  ["A promise the code did not keep", "The page said an override was \"logged against your name\"; the code recorded only the role, not the person.", "Caught by a test asserting the attribution."],
  ["An escalated case vanished", "The dashboard counted open cases by listing statuses by hand, and ESCALATED was added later.", "Caught by counting after an escalation."],
];
let by = 1.5;
bugs.forEach((b, i) => {
  panel(s, M, by, W - 2*M, 1.55, "1E2329");
  s.addShape(pres.shapes.OVAL, { x: M + 0.3, y: by + 0.52, w: 0.5, h: 0.5,
    fill: { color: THEME.colors.accent3 }, line: { color: THEME.colors.accent3, width: 1 },
    objectName: `bug-dot-${i}` });
  s.addText(String(i + 1), { x: M + 0.3, y: by + 0.52, w: 0.5, h: 0.5, fontSize: 14, bold: true,
    color: "FFFFFF", align: "center", valign: "middle", isTextBox: true, margin: 0 });
  s.addText(b[0], { x: M + 1.0, y: by + 0.18, w: 6.0, h: 0.42, fontSize: 16, bold: true,
    color: "FFFFFF", isTextBox: true, margin: 0 });
  s.addText(b[1], { x: M + 1.0, y: by + 0.62, w: 6.3, h: 0.8, fontSize: 11.5,
    color: "A9B4BE", isTextBox: true, margin: 0 });
  s.addText(b[2], { x: M + 7.6, y: by + 0.18, w: 4.2, h: 1.2, fontSize: 12, italic: true,
    color: THEME.colors.accent1, valign: "middle", isTextBox: true, margin: 0 });
  by += 1.72;
});
s.addText("None of these would fail a compile. None would be obvious in a demonstration.",
  { x: M, y: 6.5, w: 11.9, h: 0.4, fontSize: 13, italic: true, color: THEME.colors.accent4,
    isTextBox: true, margin: 0 });

// =========================================================== 8. COVERAGE ==
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "Verification" });
s.addText("Coverage, read honestly", { placeholder: "title" });
s.addChart(pres.charts.BAR, [{
  name: "Line coverage",
  labels: ["repository", "service", "dto", "domain", "config", "web"],
  values: [95.7, 85.8, 79.1, 74.7, 54.7, 27.3],
}], {
  x: M, y: 1.5, w: 7.0, h: 4.6, barDir: "bar",
  showTitle: true, title: "Line coverage by package (%)", titleFontSize: 13,
  titleColor: THEME.colors.dk2, titleFontFace: "+mn-lt",
  chartColors: [THEME.colors.accent2],
  showValue: true, dataLabelPosition: "outEnd", dataLabelFontSize: 10,
  dataLabelColor: THEME.colors.dk2, dataLabelFontFace: "+mn-lt",
  catAxisLabelColor: THEME.colors.dk2, catAxisLabelFontSize: 11, catAxisLabelFontFace: "+mn-lt",
  valAxisLabelColor: THEME.colors.accent5, valAxisLabelFontSize: 10, valAxisLabelFontFace: "+mn-lt",
  valAxisMaxVal: 100, valGridLine: { color: "E3E7EA", size: 1 },
  catGridLine: { style: "none" }, showLegend: false,
});
panel(s, 8.1, 1.5, 4.53, 4.6);
s.addText("The figure excludes 19 tests", { x: 8.4, y: 1.75, w: 4.0, h: 0.45, fontSize: 15,
  bold: true, color: THEME.colors.accent3, isTextBox: true, margin: 0 });
s.addText([
  { text: "Browser journeys drive a container in a separate JVM, which the coverage agent is not attached to.", options: { breakLine: true } },
  { text: "", options: { breakLine: true } },
  { text: "That is why web reads 27.3% while both its controllers are exercised end to end by journeys J3 and J6.", options: { breakLine: true } },
  { text: "", options: { breakLine: true } },
  { text: "Branch coverage is 49.5% — the weakest figure, and not explained away.", options: {} },
], { x: 8.4, y: 2.3, w: 4.0, h: 3.5, fontSize: 12, color: THEME.colors.dk2, isTextBox: true, margin: 0 });

// =========================================================== 9. DELIVERY ==
pres.addSection({ title: "Delivery" });
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "Delivery" });
s.addText("One artefact, promoted not rebuilt", { placeholder: "title" });
const flow = [
  ["Source", "commit 6be7e00", THEME.colors.accent5],
  ["Build", "attendance.war", THEME.colors.accent2],
  ["Image", "1.0.19-6be7e00", THEME.colors.accent1],
  ["Registry", "16 tags held", THEME.colors.accent1],
  ["Staging", ":8100", THEME.colors.accent4],
  ["Production", ":8101", THEME.colors.accent3],
];
let fx = M;
flow.forEach(([t, sub, colr], i) => {
  panel(s, fx, 1.6, 1.78, 1.5, THEME.colors.lt2);
  s.addShape(pres.shapes.OVAL, { x: fx + 0.74, y: 1.78, w: 0.3, h: 0.3, fill: { color: colr },
    line: { color: colr, width: 1 }, objectName: `flow-dot-${i}` });
  s.addText(t, { x: fx, y: 2.15, w: 1.78, h: 0.35, fontSize: 13, bold: true, color: C.text1,
    align: "center", isTextBox: true, margin: 0 });
  s.addText(sub, { x: fx, y: 2.5, w: 1.78, h: 0.35, fontSize: 9.5, color: THEME.colors.accent5,
    align: "center", isTextBox: true, margin: 0 });
  if (i < flow.length - 1) {
    s.addText("→", { x: fx + 1.78, y: 1.6, w: 0.26, h: 1.5, fontSize: 16,
      color: THEME.colors.accent5, align: "center", valign: "middle", isTextBox: true, margin: 0 });
  }
  fx += 2.04;
});
s.addText("Why the version tag matters", { x: M, y: 3.5, w: 5.6, h: 0.45, fontSize: 18, bold: true,
  color: C.text1, isTextBox: true, margin: 0 });
s.addText([
  { text: "Tags are build number plus commit: unique, and traceable to a line of code.", options: { bullet: true, breakLine: true } },
  { text: "Never only :latest — a tag that moves cannot be rolled back to.", options: { bullet: true, breakLine: true } },
  { text: "Rollback redeploys the previous tag, so recovery is a minute, not a rebuild.", options: { bullet: true } },
], { x: M, y: 4.0, w: 5.6, h: 1.8, fontSize: 13, color: THEME.colors.dk2, paraSpaceAfter: 8,
     isTextBox: true, margin: 0 });

panel(s, 6.9, 3.5, 5.73, 2.4);
s.addText("The same image in both environments", { x: 7.2, y: 3.72, w: 5.1, h: 0.4, fontSize: 15,
  bold: true, color: THEME.colors.accent2, isTextBox: true, margin: 0 });
s.addText("Nothing is rebuilt between staging and production. That is what lets \"it passed in staging\" mean something: the thing that passed is the thing that ships.",
  { x: 7.2, y: 4.2, w: 5.1, h: 1.5, fontSize: 13, color: THEME.colors.dk2, isTextBox: true, margin: 0 });

// ========================================================== 10. CONFIG ====
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "Delivery" });
s.addText("Configuration management and idempotence", { placeholder: "title" });
panel(s, M, 1.5, 5.9, 2.5);
s.addText("Ansible", { x: M + 0.3, y: 1.72, w: 5.2, h: 0.42, fontSize: 19, bold: true,
  color: THEME.colors.accent6, isTextBox: true, margin: 0 });
s.addText("Four roles — common, java, nginx, attendance_app — take a node from bare to serving the application. Agentless, over SSH.",
  { x: M + 0.3, y: 2.2, w: 5.3, h: 1.6, fontSize: 13, color: THEME.colors.dk2, isTextBox: true, margin: 0 });
panel(s, 6.9, 1.5, 5.73, 2.5);
s.addText("Puppet", { x: 7.2, y: 1.72, w: 5.1, h: 0.42, fontSize: 19, bold: true,
  color: THEME.colors.dk2, isTextBox: true, margin: 0 });
s.addText("A masterless module applied with puppet apply: install, config, deploy and service manifests, with templates for the unit file, nginx and log rotation.",
  { x: 7.2, y: 2.2, w: 5.1, h: 1.6, fontSize: 13, color: THEME.colors.dk2, isTextBox: true, margin: 0 });

panel(s, M, 4.2, W - 2*M, 1.9, THEME.colors.dk1);
s.addText("Idempotence is the property that matters", { x: M + 0.35, y: 4.42, w: 11.2, h: 0.45,
  fontSize: 18, bold: true, color: THEME.colors.accent1, isTextBox: true, margin: 0 });
s.addText("You do not say \"install Java\"; you declare \"Java 21 is present\". A first run installs it. A second changes nothing. A run against a node somebody has altered corrects the drift. Without it, re-running is a risk — so nobody re-runs, and the servers drift apart.",
  { x: M + 0.35, y: 4.95, w: 11.2, h: 1.0, fontSize: 13, color: "C9D1D9", isTextBox: true, margin: 0 });

// ========================================================== 11. RESULTS ===
pres.addSection({ title: "Results" });
s = pres.addSlide({ masterName: "CONTENT_DARK", sectionTitle: "Results" });
s.addText("Results", { placeholder: "title" });
const results = [
  ["11", "pipeline stages, all green", THEME.colors.accent1],
  ["129", "tests, zero failing", THEME.colors.accent1],
  ["16", "versioned images in the registry", THEME.colors.accent2],
  ["8", "containers forming the lab", THEME.colors.accent2],
  ["< 2 min", "to record a session (SC1)", THEME.colors.accent4],
  ["0 days", "until a student sees it (SC2)", THEME.colors.accent4],
];
results.forEach((r, i) => {
  const col = i % 3, row = Math.floor(i / 3);
  const x = M + col * 4.08, y = 1.6 + row * 2.1;
  panel(s, x, y, 3.85, 1.85, "1E2329");
  s.addText(r[0], { x: x + 0.3, y: y + 0.22, w: 3.3, h: 0.85, fontSize: 40, bold: true,
    color: r[2], isTextBox: true, margin: 0 });
  s.addText(r[1], { x: x + 0.3, y: y + 1.12, w: 3.3, h: 0.55, fontSize: 12,
    color: "A9B4BE", isTextBox: true, margin: 0 });
});
s.addText("Success criteria SC1 and SC2 were defined in Stage 1 with measured baselines, then met and re-measured in Stage 15.",
  { x: M, y: 6.1, w: 11.9, h: 0.5, fontSize: 12, italic: true, color: THEME.colors.accent5,
    isTextBox: true, margin: 0 });

// ========================================================== 12. LIMITS ====
s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "Results" });
s.addText("What this project does not solve", { placeholder: "title" });
const limits = [
  ["No database migrations", "Schema is managed by ddl-auto with no Flyway. A NOT NULL column cannot be added to a table with rows — in production that is data loss. The fix is Flyway with ddl-auto: validate."],
  ["Not truly immutable", "Containers are replaced, but the H2 file persists in a volume. Real immutability replaces the whole thing."],
  ["Observability stops at a health check", "Up or down. Nothing reports slow, or failing for a fraction of users. No metrics, no log aggregation, no alerting."],
  ["Single node", "No load balancing, no blue-green, and a window during deployment where the service is down."],
];
let liy = 1.5;
limits.forEach((l, i) => {
  panel(s, M, liy, W - 2*M, 1.2, i === 0 ? "FDEBEE" : THEME.colors.lt2);
  s.addText(l[0], { x: M + 0.3, y: liy + 0.13, w: 3.9, h: 0.9, fontSize: 15, bold: true,
    color: i === 0 ? THEME.colors.accent3 : C.text1, valign: "middle", isTextBox: true, margin: 0 });
  s.addText(l[1], { x: M + 4.3, y: liy + 0.13, w: 7.5, h: 0.95, fontSize: 11.5,
    color: THEME.colors.dk2, valign: "middle", isTextBox: true, margin: 0 });
  liy += 1.32;
});
s.addText("A weakness named first reads as understanding. The same weakness found by the examiner reads as luck.",
  { x: M, y: 6.45, w: 11.9, h: 0.4, fontSize: 13, italic: true, color: THEME.colors.accent2,
    isTextBox: true, margin: 0 });

// ========================================================== 13. CLOSE =====
s = pres.addSlide({ masterName: "TITLE_DARK", sectionTitle: "Results" });
s.addText("DevOps is replacing\n\"I think it works\" with evidence", { placeholder: "title" });
s.addText("…and automating the step so the evidence appears whether or not anyone asks", { placeholder: "body" });
s.addText([
  { text: "Swaroop Naik · Roll 23102A0001", options: { bold: true, color: "FFFFFF", breakLine: true } },
  { text: "github.com/Swaroop192005/CI-CD-Pipeline-for-a-Student-Attendance-Management", options: {} },
], { placeholder: "byline" });
const qs = [
  "How do you know it compiles?", "CI",
  "How do you know it works?", "129 tests",
  "Safe to ship?", "the gate",
  "What shipped?", "versioned tags",
  "Did the deploy work?", "health check",
];
let qx = M;
for (let i = 0; i < qs.length; i += 2) {
  chip(s, qx, 1.45, 2.3, 0.3, THEME.colors.accent1, qs[i + 1], "16191D", 9);
  qx += 2.42;
}
s.addNotes("Close on the thread: every concept answers a 'how do you know' question.");

(async () => {
  await pres.writeFile({ fileName: "CI-CD-Project-Presentation.pptx" });
  const { applyTheme } = require("/root/.claude/skills/synced/e8da7c33-e855-40f5-b0bd-e99f3816cd48_516fdda2-7fe6-413e-a679-7b4c3e156b47/pptx/scripts/apply_theme.js");
  await applyTheme("CI-CD-Project-Presentation.pptx", THEME);
  console.log("deck written and themed");
})();
