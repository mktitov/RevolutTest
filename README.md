# RevolutTest

Used:
* Scala 2.11.8
* Slick 3.1.1
* Akka 2.4.7
* Sbt 0.13.8
* h2database 1.3.175
* Scalatest 2.2.4

Build instructions:
* Install Scala 2.11.8
* Install Sbt 0.13.8
* Clone|download project to local machine
* cd to the project directory
* sbt clean test run
```log
iMac-Mikhail:RevolutTest-master tim1$ sbt clean test run
...
[info] DataManagerSuite:
[info] - User inserting should work
[info] - Adding two users with the same login should not be able
[info] - Adding accounts to user should work
[info] - Search by account number must work
[info] - Adding two accounts with the same account number should not be able
[info] - Funds could be transferred between accounts
[info] - Transfer from account with insufficient funds should not be able
[info] - Transfer from nonexisten account should not be able
[info] - Transfer to nonexisten account should not be able
[info] - Transactions list should work
[info] WebServiceSuite:
[info] - Service should list /users 
[info] - Service should list /user/{id}/accounts
[info] - Service should transfer money using /accounts/{debitAccNumber}/transfer-money?to={creditAccNumber}&amount={amount} 
[info] - Service should list /accounts/{accNumber}/transactions
[info] DbSchemaSuite:
[info] - DB Schema creating works
[info] - Adding User to Users table works
[info] - Accounts should be able to be added to Users
[info] - Accounts with the same (userId, number) should not exists
[info] Run completed in 3 seconds, 420 milliseconds.
[info] Total number of tests run: 18
[info] Suites: completed 3, aborted 0
[info] Tests: succeeded 18, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 13 s, completed 27.06.2016 1:09:33
[info] Running com.tim.revolut.RevolutApp 
[info] RevolutTest [WARN] Initializing application
[info] RevolutTest [INFO] Creating actor system
[info] RevolutTest [INFO] Creating database manager layer
[info] DataManager [INFO] Creating database schema
[info] WebService [INFO] Starting WEB server on localhost:8777
[info] DataManager [INFO] Populating test data to the database
[info] DataManager [INFO] Done
[info] Press Ctrl-C to stop...
[info] WebService [INFO] WEB Server successfully started
```

##Data structure

User (1) -> (n) Account (1) -> (n) Transaction 

* Table Users stores information about users
* Accounts stores information about user accounts
* Transactions table stores information about money transactions

#Web services

URLS:
* base url is http://localhost:8777
* __/users/__ - list all users with information about accounts count and total balance:
```json
[
  {
    name: "Bill Gates",
    totalBalance: 67,
    accountsCount: 2,
    id: 1,
    login: "bill"
  },
  {
    name: "Larry Ellison",
    totalBalance: 50,
    accountsCount: 2,
    id: 2,
    login: "larry"
  },
  {
    name: "Jeff Bezos",
    totalBalance: 47,
    accountsCount: 1,
    id: 3,
    login: "jeff"
  },
  {
    name: "Mark Zuckerberg",
    totalBalance: 41,
    accountsCount: 1,
    id: 4,
    login: "mark"
  },
  {
    name: "Larry Page",
    totalBalance: 33.2,
    accountsCount: 1,
    id: 5,
    login: "page"
  },
  {
    name: "Sergey Brin",
    totalBalance: 32.8,
    accountsCount: 1,
    id: 6,
    login: "sergey"
  },
  {
    name: "Pupkin Ivan",
    totalBalance: 0,
    accountsCount: 0,
    id: 7,
    login: "ivan"
  }
]
```
* __users/{userId}/accounts__ - shows detailed information about user accounts. 
For instance, result for __users/1/accounts__ would look like:
```json
[
  {
    userId: 1,
    number: "1001",
    balance: 37,
    id: 4
  },
  {
    userId: 1,
    number: "1002",
    balance: 30,
    id: 7
  }
]
```
* __accounts/{debitAccount}/transfer-money?to={creditAccount}&amount={amount}__ allows us to transfer funds from on account to another.
For instance, result for __/accounts/1001/transfer-money?to=2001&amount=10__ would look like:
```json
{
  successfully: true,
  txId: 1
}
```
If debit account has insufficient funds or not exists at all or credit account doesn't exists, 
we will see the transfer error message:
```json
{
  successfully: false,
  error: "Error transferring (10.0) from account (1005) to account (2001)."
}
```
* Also, using __accounts/{accountNumber}/transactions, we can see transactions.
The result of request __/accounts/1001/transactions__ may look like
```json
[
  {
    amount: 10,
    id: 1,
    debitAccountId: 4,
    time: "27.06.2016 01:47:49.409",
    creditAccountId: 6
  }
]
```
