# AllInOne External Services

A comprehensive Node.js TypeScript service providing Binance trading API functionality with real-time WebSocket support. This service handles Spot trading, USD-M Futures, and COIN-M Futures with organized route structure and production-ready architecture.

## Features

- **🏗️ Modular Architecture**: Organized route structure with separate modules for each trading service
- **📈 Spot Trading**: Complete Binance Spot trading API integration
- **⚡ USD-M Futures**: Full USD-M Futures trading functionality
- **🪙 COIN-M Futures**: Complete COIN-M Futures trading support
- **🔌 Real-time WebSocket**: Live market data subscriptions and updates
- **🛡️ Comprehensive Validation**: Input validation middleware for all endpoints
- **🚨 Error Handling**: Centralized error handling with proper HTTP status codes
- **🧪 Unit Testing**: Complete test coverage with Jest
- **📊 Health Monitoring**: Service health checks and connection status
- **🔒 Security**: Rate limiting, CORS, and security headers
- **🚀 Production Ready**: TypeScript compilation, graceful shutdown, and deployment ready

## Quick Start

### 1. Installation
```bash
git clone https://github.com/your-username/allinone-external.git
cd allinone-external
npm install
```

### 2. Environment Configuration
Create a `.env` file in the root directory:
```env
# Server Configuration
PORT=3000
NODE_ENV=development

# Binance API Configuration
BINANCE_API_KEY=your_binance_api_key_here
BINANCE_API_SECRET=your_binance_api_secret_here

# CORS Configuration
CORS_ORIGIN=*

# Rate Limiting
RATE_LIMIT_WINDOW_MS=900000
RATE_LIMIT_MAX=100
```

### 3. Binance API Setup
1. Create a Binance account and enable trading
2. Generate API keys with appropriate permissions:
   - Spot Trading (if using spot features)
   - Futures Trading (if using futures features)
3. Add your server's IP address to the API key whitelist
4. Replace the placeholder values in `.env` with your actual API credentials

### 4. Development
```bash
# Development mode with hot reload
npm run dev

# Build TypeScript
npm run build

# Production mode
npm start

# Run tests
npm test

# Type checking
npm run type-check
```

## API Documentation

### Base URL
- **Development**: `http://localhost:3000`
- **Production**: `https://your-domain.com`

### Route Structure

The API is organized into logical modules:

#### Health Check
- `GET /health` - Service health and connection status

#### Spot Trading (`/api/binance/spot/`)
- **Account & Balance**:
  - `GET /account` - Account information
  - `GET /balances` - All balances
  - `GET /balance/:asset?` - Specific asset balance

- **Order Management**:
  - `GET /orders` - Open orders
  - `POST /orders` - Place new order
  - `DELETE /orders/:symbol/:orderId` - Cancel specific order
  - `DELETE /orders/:symbol` - Cancel all orders for symbol

- **Market Data**:
  - `GET /price/:symbol?` - Price information
  - `GET /ticker/:symbol?` - 24hr ticker data
  - `GET /depth/:symbol` - Order book depth
  - `GET /trades/:symbol` - Recent trades

#### USD-M Futures (`/api/binance/futures/`)
- **Account & Positions**:
  - `GET /account` - Account information
  - `GET /positions` - Position information
  - `GET /balance/:asset?` - Balance information

- **Order Management**:
  - `GET /orders` - Open orders
  - `POST /orders` - Place new order
  - `DELETE /orders/:symbol/:orderId` - Cancel specific order
  - `DELETE /orders/:symbol` - Cancel all orders

- **Risk Management**:
  - `POST /tpsl` - Set Take Profit/Stop Loss

- **Market Data**:
  - `GET /price/:symbol?` - Price information

#### COIN-M Futures (`/api/binance/coinm/`)
- **Account & Positions**:
  - `GET /account` - Account information
  - `GET /positions` - Position information
  - `GET /balance/:asset?` - Balance information

- **Order Management**:
  - `GET /orders` - Open orders
  - `POST /orders` - Place new order
  - `DELETE /orders/:symbol/:orderId` - Cancel specific order
  - `DELETE /orders/:symbol` - Cancel all orders

- **Risk Management**:
  - `POST /tpsl` - Set Take Profit/Stop Loss

- **Market Data**:
  - `GET /price/:symbol?` - Price information

#### WebSocket Subscriptions (`/api/binance/`)
- **Spot WebSocket**:
  - `POST /spot/subscribe/ticker/:symbol` - Subscribe to ticker
  - `POST /spot/subscribe/depth/:symbol` - Subscribe to order book
  - `POST /spot/subscribe/trades/:symbol` - Subscribe to trades

- **Management**:
  - `GET /websocket/status` - WebSocket connection status

### Response Format

All API endpoints return responses in a consistent format:

```json
{
  "success": true,
  "data": {
    // Response data here
  },
  "timestamp": 1640995200000
}
```

Error responses:
```json
{
  "success": false,
  "error": "Error message here",
  "timestamp": 1640995200000
}
```

### WebSocket API

Connect to `ws://localhost:3000` (or your production WebSocket URL) for real-time updates.

#### Message Types Received:
- `welcome` - Connection established
- `ticker` - Price ticker updates
- `depth` - Order book updates
- `trade` - Trade execution updates

#### Message Types to Send:
- `ping` - Heartbeat (responds with `pong`)

## Project Structure

```
allinone-external/
├── src/
│   ├── routes/                 # Organized route modules
│   │   ├── index.ts           # Main router combining all routes
│   │   ├── health.ts          # Health check routes
│   │   ├── spot.ts            # Spot trading routes
│   │   ├── futures.ts         # USD-M futures routes
│   │   ├── coinm.ts           # COIN-M futures routes
│   │   └── websocket.ts       # WebSocket subscription routes
│   ├── services/              # Business logic services
│   │   ├── binance/           # Binance service implementations
│   │   │   ├── index.ts       # Main Binance service
│   │   │   ├── spot-rest.ts   # Spot REST API
│   │   │   ├── spot-websocket.ts # Spot WebSocket
│   │   │   ├── usdm-rest.ts   # USD-M REST API
│   │   │   ├── usdm-websocket.ts # USD-M WebSocket
│   │   │   ├── coinm-rest.ts  # COIN-M REST API
│   │   │   └── coinm-websocket.ts # COIN-M WebSocket
│   │   └── index.ts           # Service manager
│   ├── middleware/            # Express middleware
│   │   ├── errorHandler.ts    # Error handling middleware
│   │   └── validation.ts      # Input validation middleware
│   ├── utils/                 # Utility functions
│   │   ├── errors.ts          # Custom error classes
│   │   ├── logger.ts          # Logging utilities
│   │   └── validation.ts      # Validation schemas
│   ├── config/                # Configuration
│   │   └── index.ts           # App configuration
│   └── app.ts                 # Main application
├── tests/                     # Test suites
│   ├── utils/                 # Utility tests
│   ├── middleware/            # Middleware tests
│   └── integration/           # Integration tests
├── dist/                      # Compiled JavaScript
├── package.json
├── tsconfig.json
├── jest.config.js
├── .gitignore
└── README.md
```

## Testing

The application includes comprehensive test coverage:

```bash
# Run all tests
npm test

# Run tests with coverage
npm run test:coverage

# Run tests in watch mode
npm run test:watch
```

### Test Coverage
- ✅ **Validation utilities** - Input validation schemas
- ✅ **Error handling** - Custom error classes and middleware
- ✅ **Middleware** - Request validation middleware
- ✅ **Logger** - Logging functionality
- ✅ **Integration** - API endpoint testing

## Security Features

- **🛡️ Input Validation**: Comprehensive validation for all inputs
- **🚨 Error Handling**: Centralized error handling with proper status codes
- **🔒 Rate Limiting**: Protection against abuse and DDoS attacks
- **🌐 CORS**: Configurable cross-origin resource sharing
- **🔐 Security Headers**: Helmet.js for security headers
- **📝 Request Logging**: Detailed request logging for monitoring

## Deployment

### Environment Variables for Production
```env
NODE_ENV=production
BINANCE_API_KEY=your_production_api_key
BINANCE_API_SECRET=your_production_api_secret
PORT=3000
CORS_ORIGIN=https://your-frontend-domain.com
```

### Docker Deployment
```dockerfile
FROM node:18-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY dist ./dist
EXPOSE 3000
CMD ["npm", "start"]
```

### DigitalOcean (Recommended)
DigitalOcean provides static IP addresses, solving Binance's IP whitelisting requirements.

1. Create Ubuntu 22.04 droplet
2. Note the static IP address
3. Add IP to Binance API whitelist
4. Deploy using PM2 or Docker

### Heroku (Not Recommended)
Heroku uses dynamic IPs that change frequently, causing issues with Binance IP whitelisting.

## Monitoring and Health Checks

### Health Check Endpoint
```bash
curl http://localhost:3000/health
```

Response includes:
- Service status
- WebSocket connection status
- Memory usage
- Uptime
- Service initialization status

### Logging
The application provides structured logging with different levels:
- `info` - General information
- `error` - Error conditions
- `warn` - Warning conditions
- `debug` - Debug information (development only)

## Error Handling

The service includes comprehensive error handling:

### Custom Error Types
- `ApiError` - General API errors
- `ValidationError` - Input validation errors
- `AuthenticationError` - Authentication failures
- `OrderError` - Trading order errors
- `WebSocketError` - WebSocket connection errors
- `RateLimitError` - Rate limiting errors

### Error Response Format
```json
{
  "success": false,
  "error": "Detailed error message",
  "code": "ERROR_CODE",
  "timestamp": 1640995200000
}
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines
- Follow TypeScript best practices
- Write tests for new features
- Update documentation
- Use conventional commit messages
- Ensure all tests pass before submitting

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For support and questions:
- Create an issue on GitHub
- Check the [API_ENDPOINTS.md](API_ENDPOINTS.md) for detailed API documentation
- Review the test files for usage examples

## Changelog

### v1.0.0
- ✅ Initial release with organized route structure
- ✅ Complete Spot, USD-M, and COIN-M trading support
- ✅ WebSocket real-time data subscriptions
- ✅ Comprehensive test coverage
- ✅ Production-ready architecture
- ✅ Security and validation middleware
- ✅ Health monitoring and error handling