# Web Application - Health Check API

This repository is part of the **CSYE6225 Network Strctures & Cloud Computing** course, designed to implement a cloud-native web application backend with a **Health Check API**. The application provides an endpoint to check the health of the service, particularly the database connection, and is built using **Spring Boot, Hibernate** with **Maven** for dependency management.

## Objective
Using below technology stack, I implemented a Health Check API (`/healthz`) that monitors the application's connectivity with the database.

## Technology Stack
- **Programming Language**: Java
- **Framework**: Spring Boot
- **Database**: MySQL
- **ORM Framework**: Hibernate
- **Build Tool**: Apache Maven 

# Build and Deploy Instructions

### Clone the Repository
1. Download the zip file containing the source code from your GitHub repository
2. Once the repository is downloaded, unzip the project.
3. Navigate into the project directory where the source code is located.

### Set Database Connection Details
1. Create the `.env` file in the root directory of the project.
2. Add the database connection details within the `.env` file based on `application.properties`.

### Build the Project
1. Ensure that you have Apache Maven (`mvn -v`) and the Java Development Kit (JDK) installed on your system.
2. Open your terminal or command prompt and navigate to the project directory.
3. Use Maven to clean and build the project - `mvn clean install -DskipTests=true`

### Run the tests
1. mvn test

### Run the Project
1. After the project is successfully built, run the Spring Boot application - `mvn spring-boot:run`
2. The application will start on the default port `8080`.


## Testing the API

You can test the Health Check API using `curl`:

1. Check health status (GET) - `curl -vvvv http://localhost:8080/healthz`
2. Test invalid method (PUT) - `curl -vvvv -XPUT http://localhost:8080/healthz`
3. Test payload on GET - `curl -vvvv -X GET -d 'payload' http://localhost:8080/healthz`


