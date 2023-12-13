import StatisticsElement from './StatisticsElement';

function Statistics({ stats }) {
  console.log('Rendering Statistics component with property stat: ', stats);
  return (
    <div className='statistics-container'>
      {stats.commons.map((stat) => {
        return (
          <StatisticsElement
            value={stat.value}
            label={stat.label}
            iconID={''}
          />
        );
      })}
      <StatisticsElement
        value={stats.usualStore.store.name}
        label={stats.usualStore.label}
        iconID={stats.usualStore.store.iconID}
      />
    </div>
  );
}

export default Statistics;
