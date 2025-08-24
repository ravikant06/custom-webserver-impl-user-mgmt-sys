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
# add login via google
# add another microservices in spring and connect that with this user management service
# experiment about cdn
# check how can we deploy it on aws farget
# use chatgpt5 to create a beautiful fe screens
# role based auth
# to compile: mvn clean compile
# to run: mvn exec:java
# start fe server: python3 -m http.server 3000
# google auth client id 10217611478-r6n5miarsidp97u82kt9fcqcle9ftcb3.apps.googleusercontent.com
# google cleint secret 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