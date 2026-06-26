# Sawt
Tatweer Hackathon 2026 - Challenge 5 - Free Choice Al Qua'a, Al Ain, UAE
---
# The Problem

The UAE records 8,000 to 10,000 strokes every year — and nearly 50% of those patients are under the age of 45, compared to the global average where 80% of stroke victims are 65 or older. The UAE was also reported to be among the three countries in the Middle East and North Africa with the highest prevalence of stroke. (Sources: Cleveland Clinic Abu Dhabi; World Health Expo, 2024; PMC10579853)

Most stroke survivors retain some movement and can use existing rehabilitation tools. But a significant subset of severe stroke survivors — and patients with conditions such as ALS, Guillain-Barré syndrome, and severe spinal cord injury — lose the ability to speak and control their limbs, while remaining fully conscious and cognitively aware.

For these patients, standard rehabilitation tools assume some residual movement that no longer exists. They cannot press a button. They cannot swipe a screen.

Existing AAC (Augmentative and Alternative Communication) solutions — such as Tobii Dynavox or EyeGaze — require:


Dedicated medical hardware costing tens of thousands of dirhams
Clinical mounting, specialist calibration, and ongoing technical support
The patient to retain lateral eye movement — which many severe patients lose 

SAWT targets exactly this gap. It serves the patients who fall through every existing solution — patients whose primary reliable voluntary movement is blinking.

---
# Who It Is For

**Primary user**: A patient with severe motor and speech impairment following stroke or a similar neurological condition — who retains voluntary vertical eye blink but has lost all other voluntary movement and speech.

**Secondary users**:


Caregivers (family members or nurses) who need to understand what the patient needs in real time
Hospital administrators who manage communication board content across multiple patients
Emergency contacts who must be reachable instantly when a patient is in crisis


Community context: Across the UAE — including in rural communities where families care for ill relatives at home without access to specialist rehabilitation centres — there is no affordable, accessible communication tool for this patient group. A solution that works on a smartphone a family already owns, with no specialist involvement, is not just convenient. For many families, it is the only realistic option.

---

# The Solution

SAWT (Arabic for "voice") is a fully functional Android application that enables patients with severe motor impairment to communicate using nothing but deliberate eye blinks detected through the smartphone's front camera.

No specialised medical hardware. No wearables. No clinical setup. A standard Android phone and a generic phone mount or bed rail clamp — available for under 150 AED — is all that is needed.

**How it works**

The patient's phone is mounted in front of their face. The app continuously analyzes the user's eyes using Google's MediaPipe Face Landmarker. A blink is only considered valid if it closes and reopens within the patient's calibrated blink duration, reducing accidental selections.

**Navigation is entirely blink-driven**:


A single blink cycles through highlighted options on screen
Five seconds without a blink (dwell-time) selects the currently highlighted option
A double blink from the home screen enters the communication board


Per-patient calibration: A 20-second guided calibration session measures that specific patient's natural blink duration and automatically adjusts the detection window to their physiology — accounting for individual differences in blink speed.

**Patient features**


- Real-time blink detection via Google MediaPipe FaceLandmarker + CameraX
- Communication board with dynamically loaded categories (Physical Needs, Pain, Emotional State, and more)
- Single-blink zone cycling with 5-second dwell-time selection
- Pain reporting: two-step flow selecting body location then severity (Mild / Severe), composed into one message
- Yes/No quick response mode accessible from any screen
- Go Back zone on every screen — no caregiver intervention needed to navigate
- SOS zone: simultaneously triggers a phone call to the caregiver AND sends Firebase Cloud Messaging push notifications to all registered emergency contacts
- Per-patient blink calibration screen to personalise detection thresholds


**Caregiver features**


- Real-time feed of all patient communications, colour-coded by category (Physical Needs, Pain, Emotional State, SOS)
- Patient selector dropdown — one caregiver can monitor multiple patients simultaneously
- Firestore snapshot listener: feed updates the instant a patient makes a selection
- Manage patient emergency contacts
- Manage patient reminders

**Admin features**


- Manage registered users across all roles
- Manage communication board categories and submenu options stored in Firestore — changes reflect in every patient's app instantly, without a software update or developer involvement


**Testable Claims**

The following claims can be verified by running the app or reviewing the codebase.

### Valid blink detection
- Claim: A valid blink is detected within a calibrated window (default **60–500 ms**).
- How to verify: Review `PatientHomeFragment.java` (`checkBlink()` method) and `BlinkConfig.java`.

---

### Fast patient communication
- Claim: A patient can navigate to **"I am thirsty"** in under **30 seconds** from the home screen.
- How to verify: Watch the demo video and time the interaction from the first double blink to message delivery.

---

### Real-time caregiver notification
- Claim: A caregiver receives the patient's message in **under 3 seconds**.
- How to verify: Watch the demo video showing the caregiver's phone receiving the notification in real time.

---

### Simultaneous SOS alert
- Claim: SOS triggers both a phone call and an FCM push notification simultaneously.
- How to verify: Review `CommunicationFragment.java` (`triggerSOS()` method) and confirm the behavior in the demo video.

---

### Admin-controlled communication board
- Claim: Communication board content can be updated by an admin without modifying the app code.
- How to verify: Review `AdminMenuFragment.java` and the Firestore `menus` collection, which drives all displayed content.

---

### Personalized blink calibration
- Claim: Blink calibration adjusts the detection window to an individual's physiology.
- How to verify: Review `CalibrationFragment.java` and `BlinkConfig.java`.

**Testing evidence:**


Tested on 5+ able-bodied participants in adequately lit indoor environments
Double-blink to message delivery consistently achieved in under 45 seconds across all test sessions
SOS notification delivery confirmed across two physical Android devices simultaneously
Zero false positives observed during 10-minute resting sessions (eyes open, no intentional blink)
Category logging verified in Firestore — each message stored with timestamp, patient name, and category field

---

# Feasibility and Deployment

Hardware requirement: A standard Android smartphone with a front camera, plus a generic phone mount or bed rail clamp (widely available for under 150 AED). No proprietary medical hardware is required.

Software cost: Free at pilot scale. Firebase Spark plan covers authentication, Firestore, and Cloud Messaging. Cloud Functions require the Blaze pay-as-you-go plan for push notifications — negligible cost for a small deployment.

Setup time: A caregiver can onboard a new patient in under 10 minutes — register account, run blink calibration, assign emergency contacts.

Maintenance: Communication board content is managed entirely through the admin panel. No developer is needed to add messages, categories, or support additional languages. A hospital ward can customise SAWT for their specific patient population with no technical staff.

**Known constraints, honestly acknowledged:**


- Blink detection accuracy degrades in very low lighting — patients should be in adequately lit environments
- All real-time features require an internet connection — offline caching of the last-fetched menu is on the immediate roadmap
- Not yet clinically validated with patients who have actual motor impairment — current testing is with able-bodied participants in simulated conditions

**Deployment path:**


Pilot with a single neurology or rehabilitation ward at a UAE hospital
Admin panel allows the ward to customise the communication board for their patient population without developer involvement
Caregiver onboards each patient in under 10 minutes
No hardware procurement beyond a phone mount, no IT integration, no specialist training required

---

# Scalability

SAWT is architecturally designed to scale beyond this event:


Firebase backend scales horizontally with zero infrastructure changes — the same architecture that serves 10 patients serves 10,000
Admin panel means any hospital, clinic, or home caregiver can deploy and customise without developer involvement
Firestore-driven content means the communication board can be updated, translated, or expanded centrally — one admin change propagates to every patient's app instantly
Multi-patient caregiver support — a single caregiver account monitors multiple patients simultaneously via the patient selector dropdown
Language-ready — the Firestore content model supports multiple language fields; Arabic and English UI localisation is the immediate next step
Condition-agnostic — SAWT does not require a specific diagnosis. Any patient who loses speech and motor control but retains voluntary blink is a candidate, regardless of the underlying cause

Replication to other communities: SAWT requires no local infrastructure. A family caring for a relative at home in a rural community has identical access to a family in a city — the app works wherever there is a smartphone, a phone mount, and an internet connection.

**Tech stack:**


- Android (Java), minSdk 26
- Google MediaPipe FaceLandmarker — on-device face mesh and blendshape detection
- CameraX — front camera live stream
- Firebase Authentication — role-based login (patient / caregiver / admin)
- Cloud Firestore — users, menus, logs, emergency contacts
- Firebase Cloud Messaging + Cloud Functions — push notification delivery for SOS
- SharedPreferences — local blink calibration persistence

---

# How to Run

**Prerequisites**


Android Studio Hedgehog or later
Physical Android device with API 26+ and a front camera (emulator does not support live blink detection)
A Firebase project with Authentication, Firestore, and Cloud Messaging enabled

---
# Setup


- Clone this repository
- Create a Firebase project at console.firebase.google.com
- Add an Android app with package name sosina.terefe.adu.ac.ae.sawt
- Download google-services.json and place it in the app/ directory
- Enable Email/Password authentication in Firebase Console
- Deploy the Cloud Function in /functions/index.js using Firebase CLI: firebase deploy --only functions
- Open the project in Android Studio and run on a physical device

Firestore initial data

Seed the menus collection with at least one category document containing an options subcollection. Example structure:

menus/
  {docId}/
    name: "Physical Needs"
    order: 1
    options/
      {optId}/
        text: "I am thirsty"

Note on Firebase credentials

google-services.json is excluded from this repository for security. Reviewers who wish to run the app locally will need to connect their own Firebase project. To verify the app's functionality without setup, please watch the demo video.

--- 
# Demo

Watch the full demo video
(Link will be added before submission deadline)

The demo shows:


- Patient logs in and runs the 20-second blink calibration
- Patient double-blinks to enter the communication board
- Patient navigates to Physical Needs → I am thirsty using single blinks and dwell-time selection
- Caregiver phone receives the message in real time, colour-coded by category
- Patient triggers SOS — caregiver receives phone call and push notification simultaneously
- Admin updates a communication board option — change reflects on the patient's app instantly
