/***********************************************************************************
 * XE ROBOT TỰ HÀNH TRÁNH VẬT CẢN & ĐIỀU KHIỂN TỪ XA QUA BLUETOOTH
 * Cải tiến & Khắc phục lỗi:
 *  - FIX TRIỆT ĐỂ LỖI XUNG ĐỘT PHẦN CỨNG: Chuyển chân Bluetooth sang RX=2, TX=A0
 *    (Tránh xung đột nghiêm trọng giữa Servo Pin 8 và SoftwareSerial Pin 8 ở bản cũ)
 *  - Giữ nguyên toàn bộ sơ đồ chân 2 mạch cầu L298N và 3 Cảm biến siêu âm HC-SR04
 *  - Hỗ trợ giao tiếp 2 chiều: App gửi lệnh di chuyển & tốc độ PWM, Xe truyền ngược lại
 *    dữ liệu khoảng cách 3 hướng (Trước, Trái, Phải) để hiển thị Radar trên App
 *  - Chuyển chế độ linh hoạt: Tự Hành (Auto Radar) hoặc Điều Khiển Thủ Công (Manual BT)
 ***********************************************************************************/

#include <Servo.h>
#include <NewPing.h>
#include <SoftwareSerial.h>

// ==================== L298N MODULE 1 (Bánh Trái) ====================
#define L_IN1 7
#define L_IN2 6
#define L_IN3 5
#define L_IN4 4

// ==================== L298N MODULE 2 (Bánh Phải) ====================
#define R_IN1 11  
#define R_IN2 10  
#define R_IN3 9   
#define R_IN4 3   

// ==================== CẢM BIẾN SIÊU ÂM (3 HƯỚNG) ====================
#define trig_front A1
#define echo_front A2
#define trig_left  A3
#define echo_left  A4
#define trig_right 12
#define echo_right A5
#define MAX_DISTANCE 200 // cm

NewPing sonarFront(trig_front, echo_front, MAX_DISTANCE);
NewPing sonarLeft (trig_left,  echo_left,  MAX_DISTANCE);
NewPing sonarRight(trig_right, echo_right, MAX_DISTANCE);

// ==================== SERVO QUÉT RADAR ====================
#define SERVO_PIN 8
Servo radarServo;

// ==================== CÔNG TẮC CHẾ ĐỘ PHẦN CỨNG ====================
#define AUTO_SW_PIN 13

// ==================== BLUETOOTH HC-05 (FIXED PINS) ====================
// RX = 2, TX = A0 (Đảm bảo không trùng chân Servo Pin 8)
SoftwareSerial BT(2, A0);

// ==================== BIẾN ĐIỀU KHIỂN ====================
int speedPWM = 220;            // Tốc độ mặc định (0 - 255)
bool isAutoMode = false;       // Chế độ tự hành
unsigned long lastTelemTime = 0;
int lastFrontDist = 250;
int lastLeftDist = 250;
int lastRightDist = 250;

// ==================== HÀM ĐO KHOẢNG CÁCH ====================
int readFront() {
  int d = sonarFront.ping_cm();
  return (d == 0 ? 250 : d);
}

int readLeft() {
  int d = sonarLeft.ping_cm();
  return (d == 0 ? 250 : d);
}

int readRight() {
  int d = sonarRight.ping_cm();
  return (d == 0 ? 250 : d);
}

// Quét radar sang 2 bên khi phía trước bị chặn
int scanRight() {
  radarServo.write(45);
  delay(200);
  int d = readFront();
  radarServo.write(115); // Trả về giữa
  return d;
}

int scanLeft() {
  radarServo.write(175);
  delay(200);
  int d = readFront();
  radarServo.write(115); // Trả về giữa
  return d;
}

// ==================== ĐIỀU KHIỂN ĐỘNG CƠ ====================
void moveForward() {
  analogWrite(L_IN1, speedPWM); digitalWrite(L_IN2, LOW);
  analogWrite(L_IN3, speedPWM); digitalWrite(L_IN4, LOW);

  analogWrite(R_IN1, speedPWM); digitalWrite(R_IN2, LOW);
  analogWrite(R_IN3, speedPWM); digitalWrite(R_IN4, LOW);
}

void moveBackward() {
  digitalWrite(L_IN1, LOW); analogWrite(L_IN2, speedPWM);
  digitalWrite(L_IN3, LOW); analogWrite(L_IN4, speedPWM);

  digitalWrite(R_IN1, LOW); analogWrite(R_IN2, speedPWM);
  digitalWrite(R_IN3, LOW); analogWrite(R_IN4, speedPWM);
}

void turnLeft() {
  digitalWrite(L_IN1, LOW);     analogWrite(L_IN2, speedPWM);
  analogWrite(R_IN1, speedPWM); digitalWrite(R_IN2, LOW);
}

void turnRight() {
  analogWrite(L_IN1, speedPWM); digitalWrite(L_IN2, LOW);
  digitalWrite(R_IN1, LOW);     analogWrite(R_IN2, speedPWM);
}

void moveStop() {
  digitalWrite(L_IN1, LOW); digitalWrite(L_IN2, LOW);
  digitalWrite(L_IN3, LOW); digitalWrite(L_IN4, LOW);
  digitalWrite(R_IN1, LOW); digitalWrite(R_IN2, LOW);
  digitalWrite(R_IN3, LOW); digitalWrite(R_IN4, LOW);
}

// ==================== THUẬT TOÁN TỰ HÀNH TRÁNH VẬT CẢN ====================
void runAutoObstacleAvoidance() {
  int dFront = readFront();
  int dLeft  = readLeft();
  int dRight = readRight();

  lastFrontDist = dFront;
  lastLeftDist = dLeft;
  lastRightDist = dRight;

  // Nếu phía trước có vật cản gần nguy hiểm (< 35cm)
  if (dFront < 35) {
    moveStop();
    delay(100);
    moveBackward();
    delay(250);
    moveStop();
    delay(100);

    // Quét radar tìm hướng thông thoáng
    int rDist = scanRight();
    int lDist = scanLeft();

    if (rDist >= lDist && rDist > 30) {
      turnRight();
      delay(300);
    } else if (lDist > 30) {
      turnLeft();
      delay(300);
    } else {
      moveBackward();
      delay(350);
      turnRight();
      delay(350);
    }
    moveStop();
    return;
  }

  // Né vật cản 2 bên hông
  if (dLeft < 20) {
    turnRight();
    delay(150);
    return;
  }
  if (dRight < 20) {
    turnLeft();
    delay(150);
    return;
  }

  // Đường đi an toàn -> Tiến về phía trước
  moveForward();
}

// ==================== XỬ LÝ LỆNH TỪ APP BLUETOOTH ====================
void handleBluetoothCommands() {
  if (BT.available()) {
    String cmd = BT.readStringUntil('\n');
    cmd.trim();
    if (cmd.length() == 0) return;

    char c = cmd.charAt(0);

    // Điều khiển hướng di chuyển thủ công
    if (c == 'F') { isAutoMode = false; moveForward(); }
    else if (c == 'B') { isAutoMode = false; moveBackward(); }
    else if (c == 'L') { isAutoMode = false; turnLeft(); }
    else if (c == 'R') { isAutoMode = false; turnRight(); }
    else if (c == 'S') { moveStop(); }

    // Chuyển chế độ
    else if (c == 'A') { isAutoMode = true; } // Bật tự hành
    else if (c == 'M') { isAutoMode = false; moveStop(); } // Bật thủ công

    // Điều chỉnh tốc độ: V150 .. V255
    else if (c == 'V') {
      int newSpeed = cmd.substring(1).toInt();
      if (newSpeed >= 80 && newSpeed <= 255) {
        speedPWM = newSpeed;
      }
    }
  }
}

// Gửi Telemetry (Khoảng cách Radar) về App Android
void sendTelemetryToApp() {
  lastFrontDist = readFront();
  lastLeftDist = readLeft();
  lastRightDist = readRight();

  // Định dạng: D:F,L,R,speed,mode\n (Ví dụ: D:85,42,110,220,AUTO)
  BT.print("D:");
  BT.print(lastFrontDist);
  BT.print(",");
  BT.print(lastLeftDist);
  BT.print(",");
  BT.print(lastRightDist);
  BT.print(",");
  BT.print(speedPWM);
  BT.print(",");
  BT.println(isAutoMode ? "AUTO" : "MANUAL");
}

// ==================== SETUP ====================
void setup() {
  Serial.begin(115200);
  BT.begin(9600); // Tốc độ chuẩn module HC-05

  // Cấu hình chân Motor
  pinMode(L_IN1, OUTPUT); pinMode(L_IN2, OUTPUT);
  pinMode(L_IN3, OUTPUT); pinMode(L_IN4, OUTPUT);
  pinMode(R_IN1, OUTPUT); pinMode(R_IN2, OUTPUT);
  pinMode(R_IN3, OUTPUT); pinMode(R_IN4, OUTPUT);
  moveStop();

  // Công tắc Auto trên xe
  pinMode(AUTO_SW_PIN, INPUT_PULLUP);

  // Cấu hình Servo Radar
  radarServo.attach(SERVO_PIN);
  radarServo.write(115); // Góc chính diện

  Serial.println("Xe Tu Hanh San Sang!");
}

// ==================== LOOP ====================
void loop() {
  // Kiểm tra công tắc cứng (nếu gạt LOW thì ưu tiên tự hành)
  if (digitalRead(AUTO_SW_PIN) == LOW) {
    isAutoMode = true;
  }

  // Nhận lệnh từ App
  handleBluetoothCommands();

  // Chạy logic theo chế độ
  if (isAutoMode) {
    runAutoObstacleAvoidance();
  }

  // Gửi dữ liệu cảm biến về điện thoại mỗi 150ms
  unsigned long now = millis();
  if (now - lastTelemTime >= 150) {
    lastTelemTime = now;
    sendTelemetryToApp();
  }
}
