# Local Smoke Tests

Start dependencies and the app:

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

Check health:

```powershell
curl http://localhost:8080/actuator/health
```

Create a payment:

```powershell
curl -X POST http://localhost:8080/api/v1/payments `
  -H "Content-Type: application/json" `
  -H "X-API-Key: test_api_key_123" `
  -H "X-Idempotency-Key: smoke-test-001" `
  -d '{
    "amount": { "value": 10000, "currency": "INR" },
    "paymentMethod": {
      "type": "CARD",
      "card": {
        "number": "4111111111111111",
        "expiryMonth": "12",
        "expiryYear": "2027",
        "cvv": "123",
        "holderName": "Smoke Test"
      }
    },
    "merchantReference": "SMOKE-001"
  }'
```

Expected initial status is `INITIATED`. After the background processor runs, check the payment status:

```powershell
curl http://localhost:8080/api/v1/payments/{paymentId}/status `
  -H "X-API-Key: test_api_key_123"
```

Run tests:

```powershell
.\gradlew.bat test
```
