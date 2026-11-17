function ensureUser(dbName, user, pwd) {
  const target = db.getSiblingDB(dbName);
  if (!target.getUser(user)) {
    target.createUser({
      user: user,
      pwd: pwd,
      roles: [{ role: "readWrite", db: dbName }]
    });
  }
}

ensureUser("productdb", "product_svc", process.env.PRODUCT_DB_PASSWORD);
ensureUser("notificationdb", "notification_svc", process.env.NOTIFICATION_DB_PASSWORD);