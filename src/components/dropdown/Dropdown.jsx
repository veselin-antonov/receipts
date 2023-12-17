import { useState } from 'react';

function Dropdown({ options, onInput }) {
  console.log('Rendering Dropdown component with options: ', options);

  const [expanded, setExpanded] = useState(false);
  const [selectedOption, setSelectedOption] = useState(options[0]);

  console.log(
    'Rendering Dropdown component with state selectedOption: ',
    selectedOption
  );

  const renderContent = () => {
    let sortedOptions;

    if (selectedOption.value) {
      const selectedOptionIndex = options.findIndex(
        (o) => o.value == selectedOption.value
      );
      sortedOptions = [
        selectedOption,
        ...options.toSpliced(selectedOptionIndex, 1),
      ];
    } else {
      sortedOptions = options;
    }

    if (expanded) {
      return sortedOptions.map((option) => {
        return (
          <li
            key={sortedOptions.value}
            className='option'
            onClick={() => {
              setExpanded(!expanded);
              setSelectedOption(option);
              onInput(option.value);
            }}
          >
            {option.content}
          </li>
        );
      });
    } else {
      return (
        <li
          className='option'
          key={selectedOption.value}
          onClick={() => {
            setExpanded(!expanded);
            onInput(selectedOption.value);
          }}
        >
          {selectedOption.content}
        </li>
      );
    }
  };

  return (
    <div className='my-dropdown'>
      <ul className={`options-list ${expanded ? 'expanded' : ''}`}>
        {renderContent()}
      </ul>
    </div>
  );
}

export default Dropdown;
