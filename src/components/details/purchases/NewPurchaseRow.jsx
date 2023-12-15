import { useState, useEffect } from 'react';
import Dropdown from '../../dropdown/Dropdown';

const grayOutWhenEmpty = (event) => {
  const input = event.target;
  if (event.code == 'Backspace') {
    input.style.color =
      !input.validity.badInput || input.validity.valid ? '#757575' : '#000000';
  }
};

function NewPurchaseRow() {
  const [vendors, setVendors] = useState([{ name: 'Избери Магазин' }]);
  console.log('Rendering NewPurchaseRow component with vendors: ', vendors);

  useEffect(() => {
    fetch('http://localhost:8080/vendors')
      .then((data) => data.json())
      .then((vendors) =>
        setVendors([{ name: 'Избери Магазин' }].concat(vendors))
      )
      .catch((error) =>
        console.log(
          'Error while fetching vendors from "http://localhost:8080/vendors". Details: ',
          error
        )
      );
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
              grayOutWhenEmpty(e);
            }, 5);
          }}
        />
      </td>
      <td>
        <Dropdown
          options={vendors.map((store) => (
            <>
              {store.iconID && (
                <img src={'/' + store.iconID + '-icon.svg'} alt='' />
              )}
              {store.name}
            </>
          ))}
        />
      </td>
      <td>
        <input type='checkbox' />
      </td>
    </tr>
  );
}

export default NewPurchaseRow;
