// data.js — workout split + sample logs + body metrics
// Sourced from the user's illustrated 5-day split PDF.

window.SPLIT_OPTIONS = [
  {
    id: '5day-illustrated',
    name: '5 Day Illustrated',
    sub: 'Your PDF split',
    days: ['Upper', 'Lower', 'Shoulders & Arms', 'Legs (Quad)', 'Back & Chest'],
    recommended: true,
  },
  {
    id: 'ppl',
    name: 'Push Pull Legs',
    sub: '3 or 6 day variants',
    days: ['Push', 'Pull', 'Legs'],
  },
  {
    id: 'upper-lower',
    name: 'Upper / Lower',
    sub: '4 day classic',
    days: ['Upper', 'Lower', 'Upper', 'Lower'],
  },
  {
    id: 'bro',
    name: 'Bro Split',
    sub: 'One muscle a day',
    days: ['Chest', 'Back', 'Shoulders', 'Arms', 'Legs'],
  },
  {
    id: 'full-body',
    name: 'Full Body',
    sub: '3x per week',
    days: ['Full A', 'Full B', 'Full C'],
  },
  {
    id: 'custom',
    name: 'Build my own',
    sub: 'Pick the days',
    days: [],
  },
];

// Exercise library — used both for the curated 5-day split and the
// "build your own" picker. Muscles are tagged for grouping.
window.EXERCISES = {
  'wide-grip-pulldown': {
    id: 'wide-grip-pulldown',
    name: 'Wide Grip Pulldown',
    primary: ['Lats'],
    secondary: ['Biceps', 'Rear Delts'],
    cues: ['Slight lean back', 'Pull bar to upper chest', 'Drive elbows down', 'Chest lifted'],
  },
  'incline-smith-press': {
    id: 'incline-smith-press',
    name: 'Incline Smith Press',
    primary: ['Upper Chest'],
    secondary: ['Front Delts', 'Triceps'],
    cues: ['Bench 30–45°', 'Bar toward upper chest', 'Shoulder blades retracted', 'Feet planted'],
  },
  't-bar-row': {
    id: 't-bar-row',
    name: 'Chest-Supported T-Bar Row',
    primary: ['Mid Back'],
    secondary: ['Lats', 'Rear Delts'],
    cues: ['Chest on pad', 'Pull elbows back', 'Neutral spine', 'Controlled lowering'],
  },
  'lateral-raise': {
    id: 'lateral-raise',
    name: 'Dumbbell Lateral Raise',
    primary: ['Side Delts'],
    secondary: [],
    cues: ['Slight elbow bend', 'Raise to shoulder height', 'Lead with elbows', 'Avoid swinging'],
  },
  'close-grip-bench': {
    id: 'close-grip-bench',
    name: 'Close Grip Bench Press',
    primary: ['Triceps'],
    secondary: ['Chest', 'Front Delts'],
    cues: ['Hands shoulder width', 'Elbows tucked', 'Bar to lower chest', 'Wrists stacked'],
  },
  'barbell-curl': {
    id: 'barbell-curl',
    name: 'Barbell Curl',
    primary: ['Biceps'],
    secondary: ['Forearms'],
    cues: ['Elbows near torso', 'No swinging', 'Full extension', 'Squeeze at top'],
  },
  'deadlift': {
    id: 'deadlift',
    name: 'Deadlift',
    primary: ['Posterior Chain'],
    secondary: ['Glutes', 'Hams', 'Back'],
    cues: ['Neutral spine', 'Bar close to shins', 'Push through heels', 'Hips drive forward'],
  },
  'leg-extension': {
    id: 'leg-extension',
    name: 'Leg Extension',
    primary: ['Quads'],
    secondary: [],
    cues: ['Knees align with pivot', 'Extend fully', 'Avoid locking knees', 'Controlled descent'],
  },
  'seated-ham-curl': {
    id: 'seated-ham-curl',
    name: 'Seated Hamstring Curl',
    primary: ['Hamstrings'],
    secondary: [],
    cues: ['Back against pad', 'Ankles under roller', 'Curl through heels', 'Slow return'],
  },
  'seated-calf-raise': {
    id: 'seated-calf-raise',
    name: 'Seated Calf Raise',
    primary: ['Calves'],
    secondary: [],
    cues: ['Full stretch', 'Push through toes', 'Pause at top'],
  },
  'decline-crunch': {
    id: 'decline-crunch',
    name: 'Machine / Decline Crunch',
    primary: ['Abs'],
    secondary: [],
    cues: ['Curl spine', 'Exhale on contraction', 'Slow negative'],
  },
  'db-shoulder-press': {
    id: 'db-shoulder-press',
    name: 'Dumbbell Shoulder Press',
    primary: ['Shoulders'],
    secondary: ['Triceps'],
    cues: ['Core tight', 'Dumbbells near shoulders', 'Press vertically'],
  },
  'cable-lateral': {
    id: 'cable-lateral',
    name: 'Cable Lateral Raise',
    primary: ['Side Delts'],
    secondary: [],
    cues: ['Raise slightly forward', 'Small elbow bend', 'Avoid shrugging'],
  },
  'reverse-fly': {
    id: 'reverse-fly',
    name: 'Reverse Fly',
    primary: ['Rear Delts'],
    secondary: ['Mid Back'],
    cues: ['Neutral spine', 'Pull arms wide', 'Focus rear delts'],
  },
  'jm-press': {
    id: 'jm-press',
    name: 'JM Press',
    primary: ['Triceps'],
    secondary: [],
    cues: ['Bar toward upper chest', 'Elbows forward', 'Partial press path'],
  },
  'cable-tri-ext': {
    id: 'cable-tri-ext',
    name: 'Cable Tricep Extension',
    primary: ['Triceps'],
    secondary: [],
    cues: ['Elbows pinned', 'Full lockout', 'Separate rope'],
  },
  'bayesian-curl': {
    id: 'bayesian-curl',
    name: 'Bayesian Curl',
    primary: ['Biceps'],
    secondary: [],
    cues: ['Stand forward of cable', 'Arm behind torso', 'Curl without elbow moving'],
  },
  'preacher-curl': {
    id: 'preacher-curl',
    name: 'Dumbbell Preacher Curl',
    primary: ['Biceps'],
    secondary: [],
    cues: ['Arm on pad', 'Full stretch', 'Controlled lift'],
  },
  'barbell-squat': {
    id: 'barbell-squat',
    name: 'Barbell Squat',
    primary: ['Quads'],
    secondary: ['Glutes', 'Adductors'],
    cues: ['Chest up', 'Knees track toes', 'Depth parallel'],
  },
  'lying-ham-curl': {
    id: 'lying-ham-curl',
    name: 'Lying Hamstring Curl',
    primary: ['Hamstrings'],
    secondary: [],
    cues: ['Hips on bench', 'Curl through heels', 'Controlled lowering'],
  },
  'adductor-machine': {
    id: 'adductor-machine',
    name: 'Adductor Machine',
    primary: ['Adductors'],
    secondary: [],
    cues: ['Back against pad', 'Controlled squeeze', 'Slow return'],
  },
  'standing-calf': {
    id: 'standing-calf',
    name: 'Standing Calf Raise',
    primary: ['Calves'],
    secondary: [],
    cues: ['Heels drop fully', 'Push through big toe', 'Pause at top'],
  },
  'single-cable-row': {
    id: 'single-cable-row',
    name: 'Single Arm Cable Row',
    primary: ['Lats'],
    secondary: ['Mid Back', 'Biceps'],
    cues: ['Pull elbow to hip', 'Chest up', 'Avoid twisting'],
  },
  'incline-db-row': {
    id: 'incline-db-row',
    name: 'Incline Dumbbell Row',
    primary: ['Mid Back'],
    secondary: ['Lats'],
    cues: ['Chest on bench', 'Neutral neck', 'Elbows toward hips'],
  },
  'dumbbell-press': {
    id: 'dumbbell-press',
    name: 'Dumbbell Press',
    primary: ['Chest'],
    secondary: ['Front Delts', 'Triceps'],
    cues: ['Slight arch', 'Elbows 45°', 'Press above chest'],
  },
  'incline-cable-fly': {
    id: 'incline-cable-fly',
    name: 'Incline Cable Fly',
    primary: ['Upper Chest'],
    secondary: [],
    cues: ['Slight elbow bend', 'Arc motion', 'Stretch at bottom'],
  },
};

window.WORKOUT_DAYS = [
  {
    id: 'day-1',
    name: 'Upper Body',
    subtitle: 'Day 1',
    color: 'lime',
    exercises: [
      { id: 'wide-grip-pulldown', sets: 3, reps: '6-8' },
      { id: 'incline-smith-press', sets: 3, reps: '6-8' },
      { id: 't-bar-row', sets: 3, reps: '6-8' },
      { id: 'lateral-raise', sets: 3, reps: '6-8' },
      { id: 'close-grip-bench', sets: 3, reps: '6-8' },
      { id: 'barbell-curl', sets: 3, reps: '6-8' },
    ],
    warmup: [
      '5 min easy bike or rower',
      'Band pull-aparts — 2×15',
      'Empty bar bench — 1×10',
      'Scap pull-ups — 2×8',
    ],
  },
  {
    id: 'day-2',
    name: 'Lower Body',
    subtitle: 'Day 2',
    color: 'coral',
    exercises: [
      { id: 'deadlift', sets: 3, reps: '6-8' },
      { id: 'leg-extension', sets: 3, reps: '6-8' },
      { id: 'seated-ham-curl', sets: 3, reps: '6-8' },
      { id: 'seated-calf-raise', sets: 3, reps: '6-8' },
      { id: 'decline-crunch', sets: 3, reps: '6-8' },
    ],
    warmup: [
      '5 min easy bike',
      'Glute bridges — 2×12',
      'Bodyweight squats — 1×10',
      'RDL with light bar — 1×10',
    ],
  },
  {
    id: 'day-3',
    name: 'Shoulders & Arms',
    subtitle: 'Day 3',
    color: 'lime',
    exercises: [
      { id: 'db-shoulder-press', sets: 3, reps: '8-12' },
      { id: 'cable-lateral', sets: 3, reps: '8-12' },
      { id: 'reverse-fly', sets: 3, reps: '8-12' },
      { id: 'jm-press', sets: 3, reps: '8-12' },
      { id: 'cable-tri-ext', sets: 3, reps: '8-12' },
      { id: 'bayesian-curl', sets: 3, reps: '8-12' },
      { id: 'preacher-curl', sets: 3, reps: '8-12' },
    ],
    warmup: [
      '5 min light cardio',
      'Band shoulder dislocates — 2×10',
      'Wall slides — 2×10',
      'Light DB press — 1×10',
    ],
  },
  {
    id: 'day-4',
    name: 'Legs (Quad)',
    subtitle: 'Day 4',
    color: 'coral',
    exercises: [
      { id: 'barbell-squat', sets: 3, reps: '6-8' },
      { id: 'lying-ham-curl', sets: 3, reps: '8-12' },
      { id: 'leg-extension', sets: 3, reps: '8-12' },
      { id: 'adductor-machine', sets: 2, reps: '10-15' },
      { id: 'standing-calf', sets: 3, reps: '8-12' },
      { id: 'decline-crunch', sets: 3, reps: '8-12' },
    ],
    warmup: [
      '5 min easy bike',
      'Couch stretch — 30s each',
      'Bodyweight squats — 2×10',
      'Empty bar squat — 1×10',
    ],
  },
  {
    id: 'day-5',
    name: 'Back & Chest',
    subtitle: 'Day 5',
    color: 'lime',
    exercises: [
      { id: 'single-cable-row', sets: 3, reps: '8-12' },
      { id: 'incline-db-row', sets: 3, reps: '8-12' },
      { id: 'wide-grip-pulldown', sets: 3, reps: '8-12' },
      { id: 'dumbbell-press', sets: 3, reps: '8-12' },
      { id: 'incline-cable-fly', sets: 3, reps: '8-12' },
    ],
    warmup: [
      '5 min rower',
      'Band pull-aparts — 2×15',
      'Push-ups — 1×10',
      'Light DB row — 1×10',
    ],
  },
];

// --- Sample history so charts have data. Last 8 weeks. ---
// Build dates in LOCAL time and format with a local YYYY-MM-DD helper so the
// calendar / heatmap "today" never lands on the wrong cell (toISOString is UTC).
function toLocalISO(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
const today = new Date(2026, 4, 15); // local May 15, 2026 (a Friday)
function daysAgo(n) {
  const d = new Date(today);
  d.setDate(d.getDate() - n);
  return toLocalISO(d);
}

// Workout completion log: which day on which date.
// Day numbers are 1..5. Simulates a roughly 5-day rotation, 4 sessions/week.
window.WORKOUT_LOG = [
  { date: daysAgo(56), day: 1 }, { date: daysAgo(54), day: 2 }, { date: daysAgo(52), day: 3 }, { date: daysAgo(50), day: 4 },
  { date: daysAgo(47), day: 5 }, { date: daysAgo(45), day: 1 }, { date: daysAgo(43), day: 2 }, { date: daysAgo(41), day: 3 },
  { date: daysAgo(39), day: 4 }, { date: daysAgo(37), day: 5 }, { date: daysAgo(35), day: 1 }, { date: daysAgo(33), day: 2 },
  { date: daysAgo(30), day: 3 }, { date: daysAgo(28), day: 4 }, { date: daysAgo(26), day: 5 }, { date: daysAgo(24), day: 1 },
  { date: daysAgo(22), day: 2 }, { date: daysAgo(19), day: 3 }, { date: daysAgo(17), day: 4 }, { date: daysAgo(15), day: 5 },
  { date: daysAgo(12), day: 1 }, { date: daysAgo(10), day: 2 }, { date: daysAgo(8),  day: 3 }, { date: daysAgo(6),  day: 4 },
  { date: daysAgo(3),  day: 5 }, { date: daysAgo(1),  day: 1 },
];

// Weight progression for a few key exercises (kg). Each entry is the heaviest set logged that day.
window.EXERCISE_HISTORY = {
  'incline-smith-press': [
    { date: daysAgo(56), weight: 60 }, { date: daysAgo(45), weight: 62.5 },
    { date: daysAgo(35), weight: 65 }, { date: daysAgo(24), weight: 67.5 },
    { date: daysAgo(12), weight: 70 }, { date: daysAgo(1),  weight: 72.5 },
  ],
  'barbell-squat': [
    { date: daysAgo(50), weight: 100 }, { date: daysAgo(39), weight: 105 },
    { date: daysAgo(28), weight: 110 }, { date: daysAgo(17), weight: 115 },
    { date: daysAgo(6),  weight: 120 },
  ],
  'deadlift': [
    { date: daysAgo(54), weight: 120 }, { date: daysAgo(43), weight: 125 },
    { date: daysAgo(33), weight: 130 }, { date: daysAgo(22), weight: 135 },
    { date: daysAgo(10), weight: 140 },
  ],
  'wide-grip-pulldown': [
    { date: daysAgo(56), weight: 55 }, { date: daysAgo(45), weight: 57.5 },
    { date: daysAgo(35), weight: 60 }, { date: daysAgo(24), weight: 62.5 },
    { date: daysAgo(15), weight: 62.5 }, { date: daysAgo(1),  weight: 65 },
  ],
  'barbell-curl': [
    { date: daysAgo(56), weight: 25 }, { date: daysAgo(45), weight: 27.5 },
    { date: daysAgo(35), weight: 27.5 }, { date: daysAgo(24), weight: 30 },
    { date: daysAgo(1),  weight: 32.5 },
  ],
  'db-shoulder-press': [
    { date: daysAgo(52), weight: 18 }, { date: daysAgo(41), weight: 20 },
    { date: daysAgo(30), weight: 22 }, { date: daysAgo(19), weight: 22 },
    { date: daysAgo(8),  weight: 24 },
  ],
};

// Bodyweight history (kg).
window.BODYWEIGHT = [
  { date: daysAgo(56), kg: 78.2 }, { date: daysAgo(49), kg: 78.5 },
  { date: daysAgo(42), kg: 78.9 }, { date: daysAgo(35), kg: 79.4 },
  { date: daysAgo(28), kg: 79.7 }, { date: daysAgo(21), kg: 80.1 },
  { date: daysAgo(14), kg: 80.4 }, { date: daysAgo(7),  kg: 80.8 },
  { date: daysAgo(0),  kg: 81.0 },
];

// Last-used weights, used to pre-fill the "Complete Set" CTA.
window.LAST_WEIGHT = {
  'wide-grip-pulldown': 65,
  'incline-smith-press': 72.5,
  't-bar-row': 50,
  'lateral-raise': 10,
  'close-grip-bench': 65,
  'barbell-curl': 32.5,
  'deadlift': 140,
  'leg-extension': 70,
  'seated-ham-curl': 55,
  'seated-calf-raise': 70,
  'decline-crunch': 15,
  'db-shoulder-press': 24,
  'cable-lateral': 12,
  'reverse-fly': 25,
  'jm-press': 45,
  'cable-tri-ext': 32,
  'bayesian-curl': 18,
  'preacher-curl': 14,
  'barbell-squat': 120,
  'lying-ham-curl': 50,
  'adductor-machine': 60,
  'standing-calf': 85,
  'single-cable-row': 30,
  'incline-db-row': 22,
  'dumbbell-press': 30,
  'incline-cable-fly': 18,
};

window.TODAY_ISO = toLocalISO(today);
window.toLocalISO = toLocalISO;
