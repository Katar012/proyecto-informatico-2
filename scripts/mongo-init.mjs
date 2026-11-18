import { MongoClient } from 'mongodb';

const client = new MongoClient('mongodb://127.0.0.1:27017/?directConnection=true');
try {
  await client.connect();
  const admin = client.db('admin');
  try {
    await admin.command({ replSetGetStatus: 1 });
  } catch (error) {
    if (error.code !== 94) throw error;
    await admin.command({ replSetInitiate: { _id: 'rs0', members: [{ _id: 0, host: 'localhost:27017' }] } });
  }
  for (let attempt = 0; attempt < 60; attempt++) {
    if ((await admin.command({ hello: 1 })).isWritablePrimary) {
      console.log('MongoDB rs0 PRIMARY disponible.');
      break;
    }
    if (attempt === 59) throw new Error('MongoDB no alcanzo PRIMARY en 60 segundos.');
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
} finally {
  await client.close();
}
