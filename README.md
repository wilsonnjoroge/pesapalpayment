PesaPal Java Integration

A Java integration for the PesaPal Payment API, supporting payment creation, customer redirection, and transaction status verification.

Features
```
PesaPal API authentication
Create payment orders
Redirect customers to PesaPal
Handle payment callbacks/IPN
Verify transaction status
Sandbox & production support
Requirements
Java 17+
Maven
PesaPal merchant account
PesaPal Consumer Key & Consumer Secret
```

Configuration

Set the following environment variables:

```
PESAPAL_BASE_URL=https://cybqa.pesapal.com/pesapalv3
PESAPAL_CONSUMER_KEY=your-consumer-key
PESAPAL_CONSUMER_SECRET=your-consumer-secret
PESAPAL_CALLBACK_URL=https://your-domain.com/api/payments/callback
```

Use the production base URL when deploying to production.

Installation

Clone the repository and build the project:
```
git clone https://github.com/your-username/pesapal-java-integration.git
cd pesapal-java-integration
mvn clean install
```

Run the application:
```
mvn spring-boot:run
```
Payment Flow
```
Create Order
     ↓
Authenticate with PesaPal
     ↓
Submit Payment
     ↓
Get Redirect URL
     ↓
Customer Completes Payment
     ↓
Receive Callback/IPN
     ↓
Verify Transaction Status
     ↓
Update Order
```

Example

Create a payment:

```
POST /api/payments
Content-Type: application/json

{
  "merchantReference": "ORDER-10001",
  "amount": 1000.00,
  "currency": "KES",
  "email": "customer@example.com",
  "phoneNumber": "254700000000"
}
```

The API returns a PesaPal payment URL which can be used to redirect the customer.

Important

Always verify the transaction status with PesaPal from your backend before marking an order as paid.

Never commit API credentials to source control.

License

MIT License.
