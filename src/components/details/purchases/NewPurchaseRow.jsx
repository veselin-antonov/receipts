import { useState, useEffect } from 'react';
import Dropdown from '../../dropdown/Dropdown';

const grayOutWhenEmpty = (inputName, event) => {
  const input = document.getElementsByClassName('date-input')[0];
  if (
    event.code == 'Backspace' &&
    (!input.validity.badInput || input.validity.valid)
  ) {
    input.style.color = '#757575';
  } else {
    input.style.color = '#000000';
  }
};

async function getVendors(callback) {
  return setTimeout(
    () =>
      callback([{name: "Избери Магазин"}].concat([
        { name: 'Lidl', iconID: 'lidl' },
        { name: 'Billa', iconID: 'billa' },
        { name: 'Kaufland', iconID: 'kaufland' },
      ])),
    1500
  );
}

function NewPurchaseRow() {
  const [vendors, setVendors] = useState([{name: "Избери Магазин"}]);
  console.log('Rendering NewPurchaseRow component with vendors: ', vendors);

  useEffect(() => {
    getVendors(setVendors);
  }, []);

  return (
    <tr className='new-purchase-row'>
      <td>
        <input className='price-input' type='text' placeholder='Цена' />
      </td>
      <td>
        <input
          className='date-input'
          type='date'
          onKeyDown={(e) => {
            setTimeout(() => {
              grayOutWhenEmpty('date-input', e);
            }, 5);
          }}
        />
      </td>
      <td >
        <Dropdown options={vendors} />
      </td>
      <td>
        <input type="checkbox" />
      </td>
    </tr>
  );
}

export default NewPurchaseRow;
