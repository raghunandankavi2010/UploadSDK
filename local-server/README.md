# Connecting Android SDK to Local Server

## Step 1: Start the Local Server

### Option A: Python (Recommended - simpler)
```bash
cd local-server
pip3 install -r requirements.txt
python3 upload_server.py
```

### Option B: Node.js
```bash
cd local-server
npm install
npm start
```

Server will start on `http://0.0.0.0:5000`

## Step 2: Update Android SDK Base URL

### For Android Emulator:
Edit `upload-sdk/src/main/java/com/uploadsdk/di/NetworkModule.kt`:
```kotlin
@Provides
@Singleton
fun provideRetrofit(client: OkHttpClient): Retrofit {
    return Retrofit.Builder()
        .baseUrl("http://10.0.2.2:5000/api/v1/")  // Emulator localhost alias
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
```

### For Physical Android Device:
Find your Mac's local IP:
```bash
# On Mac
ifconfig | grep "inet " | grep -v 127.0.0.1
```

Then use that IP in NetworkModule:
```kotlin
.baseUrl("http://192.168.1.42:5000/api/v1/")  // Your Mac's IP
```

## Step 3: Add Internet Permission (already in manifest)
Already included in both app and SDK manifests.

## Step 4: Allow Cleartext Traffic (HTTP)

Create `app/src/main/res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">192.168.1.42</domain>
    </domain-config>
</network-security-config>
```

Update `app/src/main/AndroidManifest.xml` application tag:
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ... >
```

## Step 5: Run and Test

1. Start the server: `python3 upload_server.py`
2. Run the Android app
3. Select a file and upload
4. Check your Mac's `local-server/uploads/` folder - the file will appear there!

## Server Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/upload/init` | POST | Create upload session |
| `/api/v1/upload/chunk` | POST | Upload file chunk |
| `/api/v1/upload/commit` | POST | Finalize and reassemble |
| `/api/v1/upload/status/{id}` | GET | Check upload progress |
| `/api/v1/upload/session/refresh` | POST | Refresh session |
| `/api/v1/health` | GET | Server health check |
| `/` | GET | List completed uploads |

## Troubleshooting

**Connection refused?**
- Make sure server is running on `0.0.0.0:5000` (not just `localhost`)
- Check firewall settings on Mac
- Verify IP address for physical device

**Cleartext error?**
- Make sure `network_security_config.xml` is properly configured
- Or use HTTPS (add SSL certificates to server)

**Chunks not reassembling?**
- Check `local-server/uploads/chunks/` folder for individual chunks
- Server logs show chunk reception status
