# PNC Generic HTTP Proxy

A high-performance HTTP proxy service built on Quarkus that tracks and records external resource accesses during builds for non-Maven, non-NPM files. The service automatically creates Artifactory remote repositories based on external URLs and ensures all external dependencies are properly tracked and cached, preventing loss of build dependency information.

## Architecture

The service is built with a modular architecture:

- **Core Proxy Engine**: XNIO-based async HTTP proxy server (`HttpProxy.java`)
- **Request Handlers**: `ProxyAcceptHandler` manages incoming connections and routing
- **MITM SSL Support**: `ProxyMITMSSLServer` enables SSL interception and certificate generation
- **Repository Integration**: REST client services for Artifactory repository management
- **Authentication**: Keycloak integration with bearer token support
- **Observability**: OpenTelemetry tracing and metrics

## Key Features

- **Build Dependency Tracking**: Records all external resource accesses during builds with build ID association
- **Automatic Repository Creation**: Dynamically creates Artifactory remote repositories based on external host URLs
- **MITM SSL Proxy**: Intercept and proxy HTTPS traffic with custom CA certificates for complete tracking
- **Content Caching**: Intelligent caching with configurable storage strategies to avoid duplicate downloads
- **Repository Management**: Dynamic repository creation and content retrieval via Artifactory API
- **Authentication**: Keycloak OIDC integration with configurable security
- **High Performance**: Async I/O with configurable worker threads and connection pooling
- **Observability**: Built-in OpenTelemetry tracing and Prometheus metrics

## Technology Stack

- **Runtime**: Quarkus 3.38.1 with Java 25
- **I/O**: XNIO 3.8.17.Final for high-performance async networking
- **HTTP Client**: OkHttp 4.12.0 for repository communication
- **Repository Client**: Artifactory Java Client 2.21.3
- **Authentication**: Keycloak for OIDC/OAuth2
- **Observability**: OpenTelemetry SDK with OTLP export
- **Caching**: Caffeine cache with configurable eviction

## Prerequisites

- JDK 25+
- Maven 3.9.11+
- Running Artifactory instance (for repository operations)

## Configuration

Key configuration options in `application.yaml`:

```yaml
proxy:
  port: 8082                    # Proxy server port
  secured: true                 # Enable authentication
  worker:
    io.threads: 10              # I/O worker threads
    task.threads: 10            # Task worker threads

MITM:
  enabled: true                 # Enable MITM SSL
  ca.key: /tmp/ssl/ca.der      # CA private key
  ca.cert: /tmp/ssl/ca.crt     # CA certificate

artifactory:
  url: http://localhost:8081/artifactory
  access-token: ${ARTIFACTORY_ACCESS_TOKEN}
  project-key: NCL
```

## How It Works

The proxy service solves the problem of lost dependency tracking in non-Maven/non-NPM builds by intercepting all external resource requests:

### Build Dependency Tracking Workflow

1. **Client Request**: Build process sends HTTP/HTTPS request to external resource
2. **Proxy Interception**: Request is intercepted by the proxy service
3. **Build ID Association**: External URL is recorded and associated with the current build ID
4. **Repository Check**: Proxy checks if an Artifactory remote repository exists for the external host
5. **Repository Creation**: If not found, automatically creates a new remote repository for the host
6. **Content Retrieval**: Fetches the resource from the external URL
7. **Content Storage**: Stores the content in the appropriate Artifactory repository
8. **Response**: Returns the content to the client

### Benefits

- **Complete Dependency Tracking**: No external resource access goes unrecorded
- **Automatic Repository Management**: No manual repository configuration needed
- **Build Reproducibility**: All dependencies are cached and versioned
- **Security**: MITM SSL support ensures even HTTPS resources are tracked
- **Performance**: Intelligent caching reduces duplicate downloads

## Quick Start

1. **Build the project**:
```bash
git clone https://github.com/project-ncl/generic-http-proxy.git
cd generic-http-proxy
mvn clean compile
```

2. **Configure Artifactory connection** in `application.yaml`:
```yaml
artifactory:
  url: http://localhost:8081/artifactory
  access-token: ${ARTIFACTORY_ACCESS_TOKEN}
  project-key: NCL
```

3. **Start in development mode**:
```bash
mvn quarkus:dev
```

4. **Configure your build environment** to use the proxy:
```bash
export http_proxy=http://localhost:8082
export https_proxy=http://localhost:8082
# For builds that support proxy configuration
```

5. **Run your build** - all external resource accesses will be automatically tracked and cached

## Development

### Key Components

- **`HttpProxy`**: Main application class that starts the XNIO-based proxy server
- **`ProxyAcceptHandler`**: Handles incoming connections and manages the tracking workflow
- **`ProxyMITMSSLServer`**: Manages SSL interception and certificate generation for HTTPS tracking
- **`ArtifactoryRemoteRepositoryManager`**: Manages Artifactory repository operations and automatic repository creation
- **`ArtifactoryContentService`**: Handles content retrieval, caching, and build ID association
- **`ArtifactoryProxyResponseHelper`**: Manages the complete tracking workflow from request to response

### Testing

Run the test suite:
```bash
mvn test
```

The project includes comprehensive tests for proxy functionality, SSL handling, and repository integration.

## Production Deployment

### Docker Support

Multiple Dockerfile variants are available:
- `Dockerfile.jvm`: Standard JVM deployment
- `Dockerfile.native`: Native compilation with GraalVM
- `Dockerfile.native-distroless`: Minimal distroless image

### Monitoring

The service exposes:
- Health checks at `/q/health`
- Metrics at `/q/metrics` (Prometheus format)
- OpenTelemetry traces (configurable endpoints)

### Security Considerations

- Configure proper CA certificates for MITM functionality
- Set up Keycloak authentication for production
- Monitor proxy logs for security events
