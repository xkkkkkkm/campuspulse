export function mergeMessages(previous, incoming) {
  const messages = new Map(previous.map(message => [Number(message.id), message]));
  for (const message of incoming) if (Number.isSafeInteger(Number(message.id)) && Number(message.id) > 0) messages.set(Number(message.id), message);
  return [...messages.values()].sort((a, b) => Number(a.id) - Number(b.id));
}
export function messageRequest(content, pending, makeId = () => crypto.randomUUID()) {
  return pending?.content === content ? pending : { content, clientMessageId: makeId() };
}

// Receipt snapshots must never advance the delivery cursor or insert unseen IDs.
export function refreshReadReceipts(messages, snapshot) {
  const counts = new Map(snapshot.map(message => [Number(message.id), message.readCount]));
  return messages.map(message => counts.has(Number(message.id)) ? { ...message, readCount: counts.get(Number(message.id)) } : message);
}
