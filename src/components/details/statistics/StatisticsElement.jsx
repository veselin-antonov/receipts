function StatisticsElement({ value, label, iconID }) {
  console.log('Rendering StatisticsElement with props: ', value, label, iconID);
  return (
    <div className='statistics-element'>
      <div className='stat-value-container'>
        {iconID && (
          <img className='stat-value-icon' src={'/' + iconID + '-icon.svg'} />
        )}
        <span className='stat-value-text'>{value}</span>
      </div>
      <span className='stat-label'>{label}</span>
    </div>
  );
}

export default StatisticsElement;
