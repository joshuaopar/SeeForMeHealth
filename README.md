# See For Me Health

## Project Title
See For Me Health — A Voice-First Android Healthcare App for Visually Impaired Patients

## Problem & Solution
Visually impaired and low-literacy patients in Uganda struggle to access 
healthcare services independently. They cannot use standard apps that 
require reading, typing, or navigating menus.

See For Me Health solves this by providing a fully voice-driven Android 
app where patients speak all commands — no typing, no reading, no screen 
navigation required. Patients shake their phone to activate the mic and 
use voice to book appointments, call hospitals, or trigger emergency calls.
Hospital staff use the same app in a separate visual mode to view, 
confirm, or cancel patient appointments in real time via Firebase.

## Installation Guide For Third Party Installer

### Step 1 — Install the APK
1. Download SeeForMeHealth.apk from this repository
2. On the patient phone go to Settings
3. Go to Security or Privacy
4. Enable Install from Unknown Sources
5. Open the downloaded SeeForMeHealth.apk file
6. Tap Install and wait for it to finish

### Step 2 — First Launch Setup
1. Open the app — it will ask Are you a PATIENT or HOSPITAL STAFF
2. Say PATIENT clearly
3. The app will ask for the patient name — say it clearly
4. Confirm the name by saying YES
5. The app is now set up for that patient

### Step 3 — Assign a Gesture for Easy Access
Since the patient is visually impaired assign a shortcut so they can 
open the app without finding it on screen.

Recommended method for any Android phone:
1. Install the free app called Button Mapper from Play Store
2. Assign Volume Up double press to open See For Me Health
3. Patient can now open the app by pressing Volume Up twice

Alternative method:
1. Go to Settings then Accessibility
2. Find Accessibility Shortcut
3. Assign See For Me Health to the shortcut
4. Patient presses both Volume buttons together to open the app

### Step 4 — How the Patient Uses the App
1. Press the assigned shortcut to open the app
2. SHAKE the phone to activate the microphone
3. Say HOSPITAL to book an appointment
4. Say EMERGENCY to call a hospital
5. Say LEAVE to close the app

### Step 5 — For Hospital Staff
1. Open the app by tapping the icon
2. Say STAFF when asked
3. Enter your Staff ID and password
4. View and manage patient appointments

## Staff Login Credentials
| Hospital | Staff ID | Password |
|---|---|---|
| Mulago National Referral Hospital | MULAGO001 | @mulago123 |
| Mengo Hospital | MENGO001 | @mengo123 |

## Requirements
- Android 6.0 and above
- Internet connection for Firebase sync
- Microphone permission must be granted
- Call permission must be granted

## Tech Stack
- Java Android
- Firebase Realtime Database
- Firebase Authentication
- Android SpeechRecognizer API
- Android TextToSpeech API
- SQLite local offline storage
- SensorManager shake detection
