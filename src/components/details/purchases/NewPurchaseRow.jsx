import { useState, useEffect } from 'react';
import { subscribe, unsubscribe } from '../../../events';
import Dropdown from '../../dropdown/Dropdown';

const handleDateKeyEvent = (event) => {
  setTimeout(() => {
    const input = event.target;
    if (event.code == 'Backspace') {
      input.style.color =
        !input.validity.badInput || input.validity.valid
          ? '#757575'
          : '#000000';
    } else if (isFinite(event.key)) {
      input.style.color = '#000000';
    }
  }, 5);
};

const handleDateInputEvent = (event) => {
  const input = event.target;
  if (!input.value && (!input.validity.badInput || input.validity.valid)) {
    event.target.style.color = '#757575';
  } else {
    event.target.style.color = '#000000';
  }
};

function NewPurchaseRow({ addNewPurchase }) {
  const [vendors, setVendors] = useState([]);
  const [newPurchase, setNewPurchase] = useState({
    price: '',
    date: '',
    storeID: '',
    discount: false,
  });

  const createNewPurchase = () => {
    console.log('Creating new purchase: ', newPurchase);
    addNewPurchase(newPurchase);
  };

  console.log('Rendering NewPurchaseRow component with vendors: ', vendors);
  console.log(
    'Rendering NewPurchaseRow component with newPurchase: ',
    newPurchase
  );

  useEffect(() => {
    fetch('http://localhost:8080/vendors')
      .then((data) => data.json())
      .then((vendors) => setVendors(vendors))
      .catch((error) =>
        console.log(
          'Error while fetching vendors from "http://localhost:8080/vendors". Details: ',
          error
        )
      );
  }, []);

  useEffect(() => {
    subscribe('createNewPurchase', createNewPurchase);

    return () => {
      unsubscribe('createNewPurchase', createNewPurchase)
    }
  }, [newPurchase]);

  return (
    <tr className='new-purchase-row'>
      <td>
        <input
          className='price-input'
          type='number'
          step={0.01}
          lang='bg'
          placeholder='Цена'
          onInput={(event) => {
            console.log('Setting newPurchase price to: ', event.target);
            setNewPurchase({ ...newPurchase, price: event.target.value });
          }}
        />
      </td>
      <td>
        <input
          className='date-input'
          type='date'
          onKeyDown={handleDateKeyEvent}
          onInput={(event) => {
            handleDateInputEvent(event);
            setNewPurchase({ ...newPurchase, date: event.target.value });
          }}
        />
      </td>
      <td>
        <Dropdown
          onInput={(storeID) => setNewPurchase({ ...newPurchase, storeID: storeID })}
          s
          options={[
            { content: 'Избери Магазин' },
            ...vendors.map((store) => {
              return {
                value: store.id,
                content: (
                  <>
                    {store.iconID && (
                      <img src={'/' + store.iconID + '-icon.svg'} alt='' />
                    )}
                    {store.name}
                  </>
                ),
              };
            }),
          ]}
        />
      </td>
      <td>
        <input
          type='checkbox'
          onInput={(event) =>
            setNewPurchase({
              ...newPurchase,
              discount: event.target.value == 'on' ? true : false,
            })
          }
        />
      </td>
    </tr>
  );
}

export default NewPurchaseRow;
