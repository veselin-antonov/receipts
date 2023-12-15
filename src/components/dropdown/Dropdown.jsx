import { useState } from 'react';

function Dropdown({ options }) {
  console.log('Rendering Dropdown component with options: ', options);

  const [expanded, setExpanded] = useState(false);
  const [selectedOption, setSelectedOption] = useState(options[0]);

  return (
    <div className='my-dropdown'>
      <ul className={`options-list ${expanded ? 'expanded' : ''}`}>
        {(expanded &&
          [selectedOption]
            .concat(options.filter((o) => o != selectedOption))
            .map((option, index) => {
              return (
                <li
                key={index}
                  className='option'
                  onClick={() => {
                    setExpanded(!expanded);
                    setSelectedOption(option);
                  }}
                >
                  {option}
                </li>
              );
            })) || (
          <li className='option' onClick={() => setExpanded(!expanded)}>
            {selectedOption}
          </li>
        )}
      </ul>
    </div>
  );
}

export default Dropdown;
