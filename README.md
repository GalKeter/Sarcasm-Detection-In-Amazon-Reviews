## **Java AWS Distributed System for Sarcasm Detection in Amazon Reviews**
##### This Java project implements a distributed system on Amazon Web Services (AWS) to detect sarcasm in Amazon product reviews. The system consists of a manager node (EC2 instance) and multiple worker nodes. It leverages AWS services such as SQS for communication and S3 for storing input and output files.

### **Overview**
##### The system architecture follows a client-server model where clients, represented by local applications, send input files containing JSON-formatted reviews of products sold on Amazon to the manager node. The manager node then distributes these reviews to worker nodes for sentiment analysis and Named Entity Recognition (NER) using the Stanford NLP library to detect sarcasm. Once processed, the results are aggregated by the manager and sent back to the clients.

### **Components**
#### Manager Node
##### EC2 Instance: Manages the distribution of reviews to worker nodes and aggregates the processed results.  
##### SQS (Simple Queue Service): Facilitates communication between clients and the manager node. Receives input files from clients and distributes tasks to worker nodes.  
##### S3 (Simple Storage Service): Stores input files received from clients and the processed files containing the analysis results.

#### Worker Nodes
##### EC2 Instances: Perform sentiment analysis and NER on the reviews received from the manager node.  
##### SQS (Simple Queue Service): Receives tasks from the manager node, processes reviews, and sends the results back to the manager.

### **Workflow**
##### 1. Clients send input files containing JSON-formatted reviews to the manager node through SQS.  
##### 2. The manager node receives input files, stores them in S3, and distributes the reviews to available worker nodes via SQS.  
##### 3. Worker nodes perform sentiment analysis and NER on the reviews and send the processed results back to the manager node through SQS.  
##### 4. The manager node aggregates the results from all worker nodes, combines them into output files, and sends them back to the clients through SQS.  
##### 5. Clients receive the processed files from the manager node through SQS and can access them from S3.

### **Dependencies**
##### - AWS SDK for Java  
##### - Stanford NLP library

### **Notes**
##### - Set up AWS credentials and configure AWS SDK for Java.  
##### - Ensure proper IAM roles and permissions are set up for accessing AWS services.  
##### - Monitor SQS queues and EC2 instances for optimal performance and scalability.
