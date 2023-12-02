import SearchBar from '../search-bar/SearchBar';
import { useEffect, useState } from 'react';
import { Product } from '../../App';
import CreateButton from '../create-button/CreateButton';
import DetailsModal from '../details-modal/DetailsModal';

interface Props {
  getProducts: () => Promise<Product[]>;
}

const toListItem = (
  product: Product,
  onItemSelected: (productKey: string) => void
) => {
  return (
    <li
      className='list-item'
      key={product.id}
      data-bs-toggle='modal'
      data-bs-target='#details-modal'
      onClick={() => onItemSelected(product.id)}
    >
      <div className='icon-container'>
        <svg
          width='40'
          height='40'
          viewBox='0 0 40 40'
          fill='none'
          xmlns='http://www.w3.org/2000/svg'
        >
          <path
            d='M20 0L24.4903 13.8197H39.0211L27.2654 22.3607L31.7557 36.1803L20 27.6393L8.2443 36.1803L12.7346 22.3607L0.97887 13.8197H15.5097L20 0Z'
            fill='#D2BA40'
          />
        </svg>
      </div>
      <span className='list-item-text'>{product.name}</span>
      <button className='circle-btn'>
        <svg
          width='21'
          height='21'
          viewBox='0 0 21 21'
          fill='none'
          xmlns='http://www.w3.org/2000/svg'
        >
          <rect x='9' width='3' height='21' rx='1.5' fill='#D9D9D9' />
          <rect y='9' width='21' height='3' rx='1.5' fill='#D9D9D9' />
        </svg>
      </button>
    </li>
  );
};

const toList = (
  listItems: Product[],
  onItemSelected: (productKey: string) => void
) => {
  console.log('Items to be transformed into HTML elements: ', listItems);
  return listItems.length > 0 ? (
    <ul className='list'>
      {listItems.map((product) => toListItem(product, onItemSelected))}
    </ul>
  ) : (
    <div className='no-results-message'>Няма намерени продукти</div>
  );
};

const createNew = () => {};

function ListGroup({ getProducts }: Props) {
  const [filteredItems, setFilteredItems] = useState<Product[]>();

  useEffect(() => {
    console.log('Fetching data...');
    getProducts().then(setFilteredItems, (data) =>
      console.log('Error: ', data)
    );
  }, []);

  console.log("Initial value of 'filteredItems' was set to: ", filteredItems);

  const [selectedProduct, setSelectedProduct] = useState<Product>();

  const filterItems = (input: string) => {
    if (filteredItems && input) {
      console.log('Filtering for input: ', input);
      setFilteredItems(
        filteredItems.filter((product) => {
          return product.name.toLocaleLowerCase().includes(input.toLowerCase());
        })
      );
    }
  };

  const onSelectedItem = (productKey: string) => {
    setSelectedProduct(filteredItems?.find((p) => p.id == productKey));
  };

  return (
    <div className='main-container'>
      <DetailsModal product={selectedProduct} />
      <SearchBar handleInput={filterItems} />
      <div className='list-container'>
        {toList(filteredItems || [], onSelectedItem)}
      </div>
      <CreateButton onClickHandler={createNew}>
        РЕГИСТРИРАЙ НОВ ПРОДУКТ
      </CreateButton>
    </div>
  );
}

export default ListGroup;
