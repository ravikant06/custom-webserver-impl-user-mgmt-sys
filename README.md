user-management-system/
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/yourname/usermgmt/
│       ├── WebServer.java          # Main server
│       ├── models/User.java        # User model
│       ├── http/
│       │   ├── HttpRequest.java    # Request parser
│       │   ├── HttpResponse.java   # Response builder
│       │   └── HttpRouter.java     # Request router
│       ├── db/
│       │   ├── DatabaseManager.java # Connection pool
│       │   └── UserRepository.java  # Data access
│       └── controllers/
│           └── UserController.java  # API endpoints
├── database/init.sql               # Database schema
├── docker-compose.yml             # PostgreSQL setup
└── test-server.sh                 # Testing script


## Todos
# password encoding
# error handling
# how spring would have done it?
# tests
# frontend?
# how debug log is working and how can i see them?
# how to add ssl certificate in this
# How can i scale it to handle 50k request per minute
# support authentication and authorizations
# support authentication and authorizations

