# 🪙 Hedera Transfer App

This repository contains an application written in the **Java** programming language, specifically designed to facilitate and execute **asset and cryptocurrency transfer operations** (such as HBAR and custom Tokens) on the decentralized **Hedera Hashgraph** network.

This application serves as a practical example and utility demonstrating how to leverage the **official Hedera Java SDK** to build robust financial applications that interact with the Hedera network.

## ✨ Key Features

* **HBAR Transfer:** Perform standard transfers of the native network currency (HBAR) between two Hedera accounts.
* **Token Transfer:** Support for transferring custom tokens created via the Hedera Token Service (HTS).
* **Java SDK Utilization:** Built using the latest version of the Hedera Java SDK for secure and efficient transaction creation and submission.
* **Clear Structure:** The project is designed with an easy-to-understand and maintainable structure, ideal for learning.

---

## 🏗️ Backend/API Dependency 

This application is built to interact with and utilize a dedicated Backend Transfer API for core logic execution.

* **API Repository:** [Hedera Transfer API](https://github.com/Mohammed-Ehap-Ali-Zean-Al-Abdin/Hedera-Transfer-API.git)
* **Role:** This external API handles the complex processing and secure generation of transactions before execution on the Hedera network.
    
---

## 🛠️ Prerequisites

To build and run this application locally, you will need the following:

* **Java Development Kit (JDK):** Version 17 or newer.
* **Build Tool:** Maven or Gradle.
* **Hedera Account:** An active Hedera account ID and Private Key (for Testnet or Mainnet).

## 🚀 Installation and Setup

Follow these steps to set up and run the application locally:

### 1. Clone the Repository

```bash
git clone https://github.com/mhmdwaelanwr/Hedera-Transfer-App.git
cd Hedera-Transfer-App-Java
```

### 2. Configuration
The application requires your Hedera account credentials to operate. You should configure these settings using environment variables or a configuration file (like a `.properties` file).

| Variable | Description | Example |
| :--- | :--- | :--- |
| **`OPERATOR_ID`** | Your Hedera Account ID (`0.0.xxxxx`) | `0.0.1234567` |
| **`OPERATOR_KEY`** | Your account's Private Key | `3023020100...` |
| **`NETWORK`** | The Hedera network to use (`testnet` or `mainnet`) | `testnet` |

[Register to **Hadera**](https://portal.hedera.com/Register).

> ###### **⚠️ Security Note: Never commit your private keys to a public GitHub repository. Use environment variables or configuration files that are excluded via .gitignore.**

### 3. Build and Run the Project
***Using Maven:***
```bash
# Build the project
mvn clean install

# Run the application (you may need to adjust the main class name)
mvn exec:java -Dexec.mainClass="anwar.mlsa.hadera.aou"
```
---
## 💡 Usage
To execute a transfer operation, you must define the sender account, the recipient account, the amount, and the asset (HBAR or Token ID) to be transferred within the application's main logic.
***Example: HBAR Transfer***
```java
// Example HBAR transfer logic (should be implemented in the application)
AccountId senderId = AccountId.fromString(OPERATOR_ID);
AccountId receiverId = AccountId.fromString("0.0.7890123"); // Replace with actual receiver

TransferTransaction transaction = new TransferTransaction()
    .addHbarTransfer(senderId, Hbar.fromTinybars(-10000)) // Sender (negative value)
    .addHbarTransfer(receiverId, Hbar.fromTinybars(10000)); // Receiver (positive value)

// Execute the transaction
TransactionResponse txResponse = transaction.freezeWith(client).sign(operatorKey).execute(client);
// ... Proceed to check the receipt and validity
```

---
## 🤝 Contributing
Contributions are welcome! If you have suggestions, feature ideas, or bug fixes, please follow these steps:

**1.** Fork the repository.

**2.** Create a new feature branch (`git checkout -b feature/AmazingFeature`).

**3.** Commit your changes (`git commit -m 'Add some AmazingFeature'`).

**4.** Push to the branch (`git push origin feature/AmazingFeature`).

**5.** Open a Pull Request.

---
## 📄 License
This project is licensed under the **MIT License**.

See the LICENSE file for full details regarding usage and distribution rights.
