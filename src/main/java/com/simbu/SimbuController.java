package com.simbu;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

// ─── AWS SDK v1 imports ──────────────────────────────────────────
// Make sure you added these to pom.xml:
//   <dependency>
//     <groupId>com.amazonaws</groupId>
//     <artifactId>aws-java-sdk-sts</artifactId>
//     <version>1.12.500</version>
//   </dependency>
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;
import com.amazonaws.AmazonClientException;

@Controller
public class SimbuController {

    // ── Page routes ──────────────────────────────────────────────

    @GetMapping("/")
    public String home() {
        return "redirect:/raegan.html";
    }

    @GetMapping("/health")
    @ResponseBody
    public String health() {
        return "OK";
    }

    // ═══════════════════════════════════════════════════════════════
    //  ✅ POST /api/verify — Verifies IAM Access Keys via AWS STS
    //
    //  Called by raegan.html Tab 2 (IAM Access Keys login)
    //  AWS STS (Security Token Service) confirms the keys are real
    //  and returns the actual account ID, user ID, and ARN.
    //
    //  Request body (JSON):
    //    {
    //      "accountId"  : "123456789012",
    //      "username"   : "raegan-devops",
    //      "accessKey"  : "AKIAIOSFODNN7EXAMPLE",
    //      "secretKey"  : "wJalrXUtnFEMI/K7MDENG..."
    //    }
    //
    //  Success response:
    //    { "status": "ok", "accountId": "...", "username": "...", "arn": "..." }
    //
    //  Failure response:
    //    { "status": "error", "message": "..." }
    // ═══════════════════════════════════════════════════════════════
    @PostMapping("/api/verify")
    @ResponseBody
    public ResponseEntity<Map<String, String>> verifyIAMCredentials(
            @RequestBody Map<String, String> body) {

        String providedAccountId = body.getOrDefault("accountId", "").trim();
        String username          = body.getOrDefault("username",  "").trim();
        String accessKey         = body.getOrDefault("accessKey", "").trim();
        String secretKey         = body.getOrDefault("secretKey", "").trim();

        // ── Input validation
        if (accessKey.isEmpty() || secretKey.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("Access Key and Secret Key are required."));
        }
        if (!accessKey.startsWith("AKIA") && !accessKey.startsWith("ASIA")) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("Invalid Access Key format. Must start with AKIA or ASIA."));
        }

        try {
            // ── Build AWS credentials from what the user entered
            BasicAWSCredentials awsCredentials =
                    new BasicAWSCredentials(accessKey, secretKey);

            // ── Call AWS STS — this is the REAL AWS verification call
            //    If keys are wrong, AWS throws AmazonClientException
            AWSSecurityTokenService stsClient =
                    AWSSecurityTokenServiceClientBuilder.standard()
                            .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                            .withRegion("ap-south-1")   // ← change to your region if needed
                            .build();

            GetCallerIdentityResult identity =
                    stsClient.getCallerIdentity(new GetCallerIdentityRequest());

            // ── AWS confirmed the keys are valid ─────────────────
            String awsAccountId = identity.getAccount();  // real AWS account ID
            String awsUserId    = identity.getUserId();   // real IAM user ID
            String awsArn       = identity.getArn();      // real ARN

            // ── Optional: verify account ID matches what user typed
            if (!providedAccountId.isEmpty() && !providedAccountId.equals(awsAccountId)) {
                return ResponseEntity.status(403)
                        .body(errorResponse(
                            "Account ID mismatch. Keys belong to account " + awsAccountId +
                            " but you entered " + providedAccountId));
            }

            // ── Build success response
            Map<String, String> response = new HashMap<>();
            response.put("status",    "ok");
            response.put("accountId", awsAccountId);
            response.put("userId",    awsUserId);
            response.put("arn",       awsArn);
            // Use provided username if given, otherwise extract from ARN
            response.put("username",  username.isEmpty()
                    ? extractUsernameFromArn(awsArn)
                    : username);

            System.out.println("=== AWS STS Verified: " + awsArn + " ===");
            return ResponseEntity.ok(response);

        } catch (AmazonClientException e) {
            // ── AWS rejected the credentials
            String message = e.getMessage();
            if (message != null && message.contains("The security token included in the request is invalid")) {
                message = "Invalid Access Key ID. This key does not exist in AWS.";
            } else if (message != null && message.contains("The request signature we calculated does not match")) {
                message = "Invalid Secret Access Key. Signature mismatch.";
            } else if (message != null && message.contains("expired")) {
                message = "Credentials have expired. Please generate new access keys.";
            } else if (message != null && message.contains("not authorized")) {
                message = "Access denied. This IAM user does not have STS:GetCallerIdentity permission.";
            }
            System.out.println("=== AWS STS Auth Failed: " + message + " ===");
            return ResponseEntity.status(401)
                    .body(errorResponse(message != null ? message : "AWS authentication failed."));

        } catch (Exception e) {
            System.out.println("=== Unexpected error during AWS STS call: " + e.getMessage() + " ===");
            return ResponseEntity.status(500)
                    .body(errorResponse("Internal server error during AWS verification."));
        }
    }

    // ── Helper: extract username from ARN
    // e.g. arn:aws:iam::123456789012:user/raegan-devops  →  raegan-devops
    private String extractUsernameFromArn(String arn) {
        if (arn == null) return "unknown";
        int idx = arn.lastIndexOf('/');
        if (idx >= 0 && idx < arn.length() - 1) return arn.substring(idx + 1);
        return arn;
    }

    private Map<String, String> errorResponse(String message) {
        Map<String, String> err = new HashMap<>();
        err.put("status",  "error");
        err.put("message", message);
        return err;
    }

    // ═══════════════════════════════════════════════════════════════
    //  ✅ POST /api/verify-user — IAM User Login (Username + Password)
    //
    //  Called by raegan.html Tab 1 (IAM User Login)
    //
    //  How it works:
    //    1. User supplies their IAM username + password
    //    2. We look up their stored Access Key for that username
    //    3. We call AWS STS GetCallerIdentity to confirm the keys
    //       are valid (proves the user identity is real in AWS)
    //    4. We verify the password matches what was registered
    //
    //  To register a user, POST /api/register-user:
    //    { "username": "raegan-devops", "password": "MyPass123!",
    //      "accessKey": "AKIA...", "secretKey": "...",
    //      "accountId": "123456789012" }
    //
    //  Request body (JSON):
    //    { "accountId": "123456789012",
    //      "username":  "raegan-devops",
    //      "password":  "MyPass123!" }
    //
    //  Success response:
    //    { "status": "ok", "accountId": "...", "username": "...", "arn": "..." }
    //
    //  Failure response:
    //    { "status": "error", "message": "..." }
    // ═══════════════════════════════════════════════════════════════

    // ── In-memory IAM user store  (username → stored data)
    //    In production replace this with a database or AWS Secrets Manager
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.Map<String, String>>
        IAM_USER_STORE = new java.util.concurrent.ConcurrentHashMap<>();

    @PostMapping("/api/verify-user")
    @ResponseBody
    public ResponseEntity<Map<String, String>> verifyIAMUser(
            @RequestBody Map<String, String> body) {

        String accountId = body.getOrDefault("accountId", "").trim();
        String username  = body.getOrDefault("username",  "").trim();
        String password  = body.getOrDefault("password",  "").trim();

        // ── Input validation ─────────────────────────────────────
        if (username.isEmpty() || password.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("Username and password are required."));
        }

        // ── Look up user in store ────────────────────────────────
        Map<String, String> storedUser = IAM_USER_STORE.get(username);
        if (storedUser == null) {
            return ResponseEntity.status(401)
                    .body(errorResponse("IAM user not found. Please register first via /api/register-user."));
        }

        // ── Verify password ──────────────────────────────────────
        String storedPassword = storedUser.getOrDefault("password", "");
        if (!storedPassword.equals(password)) {
            return ResponseEntity.status(401)
                    .body(errorResponse("Incorrect password."));
        }

        // ── Retrieve stored IAM keys for this user ───────────────
        String storedAccessKey = storedUser.getOrDefault("accessKey", "");
        String storedSecretKey = storedUser.getOrDefault("secretKey", "");
        String storedAccountId = storedUser.getOrDefault("accountId", "");

        // ── Verify stored keys are still valid via AWS STS ────────
        try {
            BasicAWSCredentials awsCredentials =
                    new BasicAWSCredentials(storedAccessKey, storedSecretKey);

            AWSSecurityTokenService stsClient =
                    AWSSecurityTokenServiceClientBuilder.standard()
                            .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                            .withRegion("ap-south-1")
                            .build();

            GetCallerIdentityResult identity =
                    stsClient.getCallerIdentity(new GetCallerIdentityRequest());

            String awsAccountId = identity.getAccount();
            String awsArn       = identity.getArn();

            // ── Optional: verify account ID if provided ──────────
            if (!accountId.isEmpty() && !accountId.equals(awsAccountId)) {
                return ResponseEntity.status(403)
                        .body(errorResponse(
                            "Account ID mismatch. Keys belong to account " + awsAccountId));
            }

            // ── Success ──────────────────────────────────────────
            Map<String, String> response = new HashMap<>();
            response.put("status",    "ok");
            response.put("accountId", awsAccountId);
            response.put("username",  username);
            response.put("arn",       awsArn);

            System.out.println("=== IAM User Login OK: " + username + " / " + awsArn + " ===");
            return ResponseEntity.ok(response);

        } catch (AmazonClientException e) {
            String message = e.getMessage();
            if (message != null && message.contains("The security token included in the request is invalid")) {
                message = "Stored IAM keys are invalid. Re-register this user.";
            } else if (message != null && message.contains("The request signature we calculated does not match")) {
                message = "Stored IAM Secret Key mismatch. Re-register this user.";
            } else if (message != null && message.contains("expired")) {
                message = "IAM credentials have expired. Re-register with fresh keys.";
            }
            return ResponseEntity.status(401)
                    .body(errorResponse(message != null ? message : "AWS authentication failed."));

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(errorResponse("Internal server error: " + e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ✅ POST /api/register-user — Register an IAM user with password
    //
    //  Run this ONCE per user (via curl or Postman) to store their
    //  credentials so they can log in with username + password.
    //
    //  curl -X POST http://<YOUR_IP>:30092/api/register-user \
    //    -H "Content-Type: application/json" \
    //    -d '{"username":"raegan-devops","password":"MyPass123!",
    //          "accessKey":"AKIA...","secretKey":"...","accountId":"123456789012"}'
    // ═══════════════════════════════════════════════════════════════
    @PostMapping("/api/register-user")
    @ResponseBody
    public ResponseEntity<Map<String, String>> registerUser(
            @RequestBody Map<String, String> body) {

        String username  = body.getOrDefault("username",  "").trim();
        String password  = body.getOrDefault("password",  "").trim();
        String accessKey = body.getOrDefault("accessKey", "").trim();
        String secretKey = body.getOrDefault("secretKey", "").trim();
        String accountId = body.getOrDefault("accountId", "").trim();

        if (username.isEmpty() || password.isEmpty() || accessKey.isEmpty() || secretKey.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("username, password, accessKey, and secretKey are all required."));
        }
        if (!accessKey.startsWith("AKIA") && !accessKey.startsWith("ASIA")) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("Invalid Access Key format. Must start with AKIA or ASIA."));
        }
        if (password.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("Password must be at least 8 characters."));
        }

        // ── Validate keys against AWS STS before storing ─────────
        try {
            BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);
            AWSSecurityTokenService stsClient =
                    AWSSecurityTokenServiceClientBuilder.standard()
                            .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                            .withRegion("ap-south-1")
                            .build();
            stsClient.getCallerIdentity(new GetCallerIdentityRequest());
        } catch (AmazonClientException e) {
            return ResponseEntity.status(401)
                    .body(errorResponse("AWS keys are invalid: " + e.getMessage()));
        }

        // ── Store the user ────────────────────────────────────────
        Map<String, String> userData = new HashMap<>();
        userData.put("password",  password);
        userData.put("accessKey", accessKey);
        userData.put("secretKey", secretKey);
        userData.put("accountId", accountId);
        IAM_USER_STORE.put(username, userData);

        System.out.println("=== Registered IAM user: " + username + " ===");

        Map<String, String> response = new HashMap<>();
        response.put("status",  "ok");
        response.put("message", "User '" + username + "' registered successfully. They can now log in.");
        return ResponseEntity.ok(response);
    }
}
