function subscribe(eventName, listener) {
  document.addEventListener(eventName, listener);
}

function unsubscribe(eventName, listener) {
  document.removeEventListener(eventName, listener);
}

function publish(eventName, data) {
    console.log('Firing event - ', eventName, ' with data: ', data);
  const event = new CustomEvent(eventName, { detail: data });
  document.dispatchEvent(event);
}

export { publish, subscribe, unsubscribe };