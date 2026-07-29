export function formatTime(dateStr) {
  const date = new Date(dateStr);
  const year = date.getUTCFullYear()
  const month = String(date.getUTCMonth() + 1).padStart(2, '0')
  const day = String(date.getUTCDate()).padStart(2, '0')
  const hours = String(date.getUTCHours()).padStart(2, '0')
  const minutes = String(date.getUTCMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

export function formatTime2(dateStr) {
  console.log('dateStr:'+dateStr)
  const [datePart, timePart] = dateStr.split(' ');
  const [hours, minutes] = timePart.split(':');
  return `${datePart} ${hours}:${minutes}`;
}