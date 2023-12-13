import { useState } from 'react';

function Dropdown({ options }) {
  console.log('Rendering Dropdown component with options: ', options);

  const [expanded, setExpanded] = useState(false);
  const [selectedOption, setSelectedOption] = useState(options[0]);

  return (
    <div className='my-dropdown'>
      <ul className={`options-list ${expanded ? 'expanded' : ''}`}>
        {(expanded &&
          [selectedOption].concat(options).map((option) => {
            return (
              <li
                className='option'
                onClick={() => {
                  setExpanded(!expanded);
                  setSelectedOption(option);
                }}
              >
                {option.iconID && (
                  <img src={'/' + option.iconID + '-icon.svg'} alt='' />
                )}
                {option.name}
              </li>
            );
          })) || (
          <li className='option' onClick={() => setExpanded(!expanded)}>
            {selectedOption.iconID && (
              <img src={'/' + selectedOption.iconID + '-icon.svg'} alt='' />
            )}
            {selectedOption.name}
          </li>
        )}
      </ul>
    </div>
  );
}

export default Dropdown;
